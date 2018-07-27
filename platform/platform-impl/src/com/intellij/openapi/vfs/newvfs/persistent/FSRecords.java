/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.*;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.storage.*;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author max
 */
@SuppressWarnings("HardCodedStringLiteral")
public class FSRecords {
  private static final Logger LOG = Logger.getInstance("#com.intellij.vfs.persistent.FSRecords");

  public static final boolean weHaveContentHashes = SystemProperties.getBooleanProperty("idea.share.contents", true);
  private static final boolean lazyVfsDataCleaning = SystemProperties.getBooleanProperty("idea.lazy.vfs.data.cleaning", true);
  private static final boolean backgroundVfsFlush = SystemProperties.getBooleanProperty("idea.background.vfs.flush", true);
  private static final boolean inlineAttributes = SystemProperties.getBooleanProperty("idea.inline.vfs.attributes", true);
  private static final boolean bulkAttrReadSupport = SystemProperties.getBooleanProperty("idea.bulk.attr.read", false);
  private static final boolean useCompressionUtil = SystemProperties.getBooleanProperty("idea.use.lightweight.compression.for.vfs", false);
  private static final boolean useSmallAttrTable = SystemProperties.getBooleanProperty("idea.use.small.attr.table.for.vfs", true);
  static final String VFS_FILES_EXTENSION = System.getProperty("idea.vfs.files.extension", ".dat");
  private static final boolean ourStoreRootsSeparately = SystemProperties.getBooleanProperty("idea.store.roots.separately", false);

  private static final int VERSION = 21 + (weHaveContentHashes ? 0x10 : 0) + (IOUtil.ourByteBuffersUseNativeByteOrder ? 0x37 : 0) +
                                     31 + (bulkAttrReadSupport ? 0x27 : 0) + (inlineAttributes ? 0x31 : 0) +
                                     (ourStoreRootsSeparately ? 0x63 : 0) +
                                     (useCompressionUtil ? 0x7f : 0) + (useSmallAttrTable ? 0x31 : 0) +
                                     (PersistentHashMapValueStorage.COMPRESSION_ENABLED ? 21 : 0);

  private static final int PARENT_OFFSET = 0;
  private static final int PARENT_SIZE = 4;
  private static final int NAME_OFFSET = PARENT_OFFSET + PARENT_SIZE;
  private static final int NAME_SIZE = 4;
  private static final int FLAGS_OFFSET = NAME_OFFSET + NAME_SIZE;
  private static final int FLAGS_SIZE = 4;
  private static final int ATTR_REF_OFFSET = FLAGS_OFFSET + FLAGS_SIZE;
  private static final int ATTR_REF_SIZE = 4;
  private static final int CONTENT_OFFSET = ATTR_REF_OFFSET + ATTR_REF_SIZE;
  private static final int CONTENT_SIZE = 4;
  private static final int TIMESTAMP_OFFSET = CONTENT_OFFSET + CONTENT_SIZE;
  private static final int TIMESTAMP_SIZE = 8;
  private static final int MOD_COUNT_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
  private static final int MOD_COUNT_SIZE = 4;
  private static final int LENGTH_OFFSET = MOD_COUNT_OFFSET + MOD_COUNT_SIZE;
  private static final int LENGTH_SIZE = 8;

  private static final int RECORD_SIZE = LENGTH_OFFSET + LENGTH_SIZE;

  private static final byte[] ZEROES = new byte[RECORD_SIZE];

  private static final int HEADER_VERSION_OFFSET = 0;
  //private static final int HEADER_RESERVED_4BYTES_OFFSET = 4; // reserved
  private static final int HEADER_GLOBAL_MOD_COUNT_OFFSET = 8;
  private static final int HEADER_CONNECTION_STATUS_OFFSET = 12;
  private static final int HEADER_TIMESTAMP_OFFSET = 16;
  private static final int HEADER_SIZE = HEADER_TIMESTAMP_OFFSET + 8;

  private static final int CONNECTED_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f;
  private static final int CORRUPTED_MAGIC = 0xabcf7f7f;

  private static final FileAttribute ourChildrenAttr = new FileAttribute("FsRecords.DIRECTORY_CHILDREN");

  public final ReentrantReadWriteLock lock;
  public final ReentrantReadWriteLock.ReadLock r;
  public final ReentrantReadWriteLock.WriteLock w;

  private volatile int ourLocalModificationCount;
  private volatile boolean ourIsDisposed;

  private static final int FREE_RECORD_FLAG = 0x100;
  private static final int ALL_VALID_FLAGS = PersistentFS.ALL_VALID_FLAGS | FREE_RECORD_FLAG;

  private final DbConnection myDbConnection = new DbConnection(this);
  private final FileNameCache myFileNameCache = new FileNameCache(this);

  public static final FSRecords INSTANCE = new FSRecords();

  private FSRecords() {
    //noinspection ConstantConditions
    assert HEADER_SIZE <= RECORD_SIZE;

    lock = new ReentrantReadWriteLock();
    r = lock.readLock();
    w = lock.writeLock();
  }

  void writeAttributesToRecord(int id, int parentId, @NotNull FileAttributes attributes, @NotNull String name) {
    writeAndHandleErrors(() -> {
      setName(id, name);

      setTimestamp(id, attributes.lastModified);
      setLength(id, attributes.isDirectory() ? -1L : attributes.length);

      setFlags(id, (attributes.isDirectory() ? PersistentFS.IS_DIRECTORY_FLAG : 0) |
                   (attributes.isWritable() ? 0 : PersistentFS.IS_READ_ONLY) |
                   (attributes.isSymLink() ? PersistentFS.IS_SYMLINK : 0) |
                   (attributes.isSpecial() ? PersistentFS.IS_SPECIAL : 0) |
                   (attributes.isHidden() ? PersistentFS.IS_HIDDEN : 0), true);
      setParent(id, parentId);
    });
  }

  @Contract("_->fail")
  void requestVfsRebuild(@NotNull Throwable e) {
    myDbConnection.handleError(e);
  }

  @NotNull
  public File basePath() {
    return new File(myDbConnection.getCachesDir());
  }

  private static class DbConnection {
    private boolean ourInitialized;

    private PersistentStringEnumerator myNames;
    private Storage myAttributes;
    private RefCountingStorage myContents;
    private ResizeableMappedFile myRecords;
    private PersistentBTreeEnumerator<byte[]> myContentHashesEnumerator;
    private File myRootsFile;
    private final VfsDependentEnum<String> myAttributesList;
    private final TIntArrayList myFreeRecords = new TIntArrayList();

    private volatile boolean myDirty;
    /**
     * accessed under {@link #r}/{@link #w}
     */
    private ScheduledFuture<?> myFlushingFuture;
    /**
     * accessed under {@link #r}/{@link #w}
     */
    private boolean myCorrupted;

    private final FSRecords myFSRecords;

    public DbConnection(FSRecords myFSRecords) {
      this.myFSRecords = myFSRecords;
      this.myAttributesList = new VfsDependentEnum<>("attrib", EnumeratorStringDescriptor.INSTANCE, 1, new File(getCachesDir())); // inlined basePath
    }

    private static final AttrPageAwareCapacityAllocationPolicy REASONABLY_SMALL = new AttrPageAwareCapacityAllocationPolicy();

    public void connect() {
      myFSRecords.writeAndHandleErrors(() -> {
        if (!ourInitialized) {
          init();
          setupFlushing();
          ourInitialized = true;
        }
      });
    }

    private void scanFreeRecords() {
      final int fileLength = (int)myRecords.length();
      LOG.assertTrue(fileLength % RECORD_SIZE == 0, "invalid file size: " + fileLength);

      int count = fileLength / RECORD_SIZE;
      for (int n = 2; n < count; n++) {
        if (BitUtil.isSet(myFSRecords.getFlags(n), FREE_RECORD_FLAG)) {
          myFreeRecords.add(n);
        }
      }
    }

    int getFreeRecord() {
      if (myFreeRecords.isEmpty()) return 0;
      return myFreeRecords.remove(myFreeRecords.size() - 1);
    }

    private void createBrokenMarkerFile(@Nullable Throwable reason) {
      final File brokenMarker = getCorruptionMarkerFile();

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (PrintStream stream = new PrintStream(out)) {
        new Exception().printStackTrace(stream);
        if (reason != null) {
          stream.print("\nReason:\n");
          reason.printStackTrace(stream);
        }
      }
      LOG.info("Creating VFS corruption marker; Trace=\n" + out);

      try (FileWriter writer = new FileWriter(brokenMarker)) {
        writer.write("These files are corrupted and must be rebuilt from the scratch on next startup");
      }
      catch (IOException e) {
        // No luck.
      }
    }

    private File getCorruptionMarkerFile() {
      return new File(myFSRecords.basePath(), "corruption.marker");
    }

    private void init() {
      final File basePath = myFSRecords.basePath().getAbsoluteFile();
      basePath.mkdirs();

      final File namesFile = new File(basePath, "names" + VFS_FILES_EXTENSION);
      final File attributesFile = new File(basePath, "attrib" + VFS_FILES_EXTENSION);
      final File contentsFile = new File(basePath, "content" + VFS_FILES_EXTENSION);
      final File contentsHashesFile = new File(basePath, "contentHashes" + VFS_FILES_EXTENSION);
      final File recordsFile = new File(basePath, "records" + VFS_FILES_EXTENSION);
      myRootsFile = ourStoreRootsSeparately ? new File(basePath, "roots" + VFS_FILES_EXTENSION) : null;

      final File vfsDependentEnumBaseFile = VfsDependentEnum.getBaseFile(myFSRecords);

      if (!namesFile.exists()) {
        invalidateIndex("'" + namesFile.getPath() + "' does not exist");
      }

      try {
        if (getCorruptionMarkerFile().exists()) {
          invalidateIndex("corruption marker found");
          throw new IOException("Corruption marker file found");
        }

        PagedFileStorage.StorageLockContext storageLockContext = new PagedFileStorage.StorageLockContext(false);
        myNames = new PersistentStringEnumerator(namesFile, storageLockContext);

        myAttributes = new Storage(attributesFile.getPath(), REASONABLY_SMALL) {
          @Override
          protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
            return inlineAttributes && useSmallAttrTable
                   ? new CompactRecordsTable(recordsFile, pool, false)
                   : super.createRecordsTable(pool, recordsFile);
          }
        };
        myContents = new RefCountingStorage(contentsFile.getPath(), CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH,
                                            useCompressionUtil) {
          @NotNull
          @Override
          protected ExecutorService createExecutor() {
            return SequentialTaskExecutor.createSequentialApplicationPoolExecutor("FSRecords Pool");
          }
        }; // sources usually zipped with 4x ratio
        myContentHashesEnumerator =
          weHaveContentHashes ? new ContentHashesUtil.HashEnumerator(contentsHashesFile, storageLockContext) : null;
        boolean aligned = PagedFileStorage.BUFFER_SIZE % RECORD_SIZE == 0;
        assert aligned; // for performance
        myRecords = new ResizeableMappedFile(recordsFile, 20 * 1024, storageLockContext,
                                             PagedFileStorage.BUFFER_SIZE, aligned, IOUtil.ourByteBuffersUseNativeByteOrder);

        boolean initial = myRecords.length() == 0;

        if (initial) {
          cleanRecord(0); // Clean header
          cleanRecord(1); // Create root record
          setCurrentVersion();
        }

        final int recordsVersion = myRecords.getInt(HEADER_VERSION_OFFSET);

        if (myAttributes.getVersion() != recordsVersion || recordsVersion != VERSION) {
          throw new IOException("FS repository version mismatch");
        }

        if (myContents.getVersion() != recordsVersion) {
          final int fileLength = (int)myRecords.length();
          LOG.assertTrue(fileLength % RECORD_SIZE == 0, "invalid file size: " + fileLength);
          myContents.setVersion(recordsVersion);

          int count = fileLength / RECORD_SIZE;
          for (int n = 2; n < count; n++) {
            if (!BitUtil.isSet(myFSRecords.getFlags(n), PersistentFS.IS_DIRECTORY_FLAG)) {
              myFSRecords.setContentRecordId(n, 0);
            }
          }
        }

        if (myRecords.getInt(HEADER_CONNECTION_STATUS_OFFSET) != SAFELY_CLOSED_MAGIC) {
          throw new IOException("FS repository wasn't safely shut down");
        }
        if (initial) {
          markDirty();
        }
        scanFreeRecords();
      }
      catch (Exception e) { // IOException, IllegalArgumentException
        LOG.info("Filesystem storage is corrupted or does not exist. [Re]Building. Reason: " + e.getMessage());
        try {
          closeFiles();

          boolean deleted = FileUtil.delete(getCorruptionMarkerFile());
          deleted &= IOUtil.deleteAllFilesStartingWith(namesFile);
          deleted &= AbstractStorage.deleteFiles(attributesFile.getPath());
          deleted &= AbstractStorage.deleteFiles(contentsFile.getPath());
          deleted &= IOUtil.deleteAllFilesStartingWith(contentsHashesFile);
          deleted &= IOUtil.deleteAllFilesStartingWith(recordsFile);
          deleted &= IOUtil.deleteAllFilesStartingWith(vfsDependentEnumBaseFile);
          deleted &= myRootsFile == null || IOUtil.deleteAllFilesStartingWith(myRootsFile);

          if (!deleted) {
            throw new IOException("Cannot delete filesystem storage files");
          }
        }
        catch (final IOException e1) {
          final Runnable warnAndShutdown = () -> {
            if (ApplicationManager.getApplication().isUnitTestMode()) {
              //noinspection CallToPrintStackTrace
              e1.printStackTrace();
            }
            else {
              final String message = "Files in " + basePath.getPath() + " are locked.\n" +
                                     ApplicationNamesInfo.getInstance().getProductName() + " will not be able to start up.";
              if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
                JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), message, "Fatal Error", JOptionPane.ERROR_MESSAGE);
              }
              else {
                //noinspection UseOfSystemOutOrSystemErr
                System.err.println(message);
              }
            }
            Runtime.getRuntime().halt(1);
          };

          if (EventQueue.isDispatchThread()) {
            warnAndShutdown.run();
          }
          else {
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(warnAndShutdown);
          }

          throw new RuntimeException("Can't rebuild filesystem storage ", e1);
        }

        init();
      }
    }

    private static void invalidateIndex(@NotNull String reason) {
      LOG.info("Marking VFS as corrupted: " + reason);
      final File indexRoot = PathManager.getIndexRoot();
      if (indexRoot.exists()) {
        final String[] children = indexRoot.list();
        if (children != null && children.length > 0) {
          // create index corruption marker only if index directory exists and is non-empty
          // It is incorrect to consider non-existing indices "corrupted"
          FileUtil.createIfDoesntExist(new File(PathManager.getIndexRoot(), "corruption.marker"));
        }
      }
    }

    @NotNull
    private String getCachesDir() {
      String dir = System.getProperty("caches_dir");
      return dir == null ? PathManager.getSystemPath() + "/caches/" : dir;
    }

    private void markDirty() {
      assert myFSRecords.lock.isWriteLocked();
      if (!myDirty) {
        myDirty = true;
        myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, CONNECTED_MAGIC);
      }
    }

    private void setupFlushing() {
      if (!backgroundVfsFlush) {
        return;
      }

      myFlushingFuture = FlushingDaemon.everyFiveSeconds(new Runnable() {
        private int lastModCount;

        @Override
        public void run() {
          if (lastModCount == myFSRecords.ourLocalModificationCount) {
            flush();
          }
          lastModCount = myFSRecords.ourLocalModificationCount;
        }
      });
    }

    private void doForce() {
      // avoid NPE when close has already taken place
      if (myNames != null && myFlushingFuture != null) {
        myNames.force();
        myAttributes.force();
        myContents.force();
        if (myContentHashesEnumerator != null) myContentHashesEnumerator.force();
        markClean();
        myRecords.force();
      }
    }

    // must not be run under write lock to avoid other clients wait for read lock
    private void flush() {
      if (isDirty() && !HeavyProcessLatch.INSTANCE.isRunning()) {
        myFSRecords.readAndHandleErrors(() -> {
          doForce();
          return null;
        });
      }
    }

    public boolean isDirty() {
      return myDirty || myNames.isDirty() || myAttributes.isDirty() || myContents.isDirty() || myRecords.isDirty() ||
             myContentHashesEnumerator != null && myContentHashesEnumerator.isDirty();
    }


    private int getVersion() {
      final int recordsVersion = myRecords.getInt(HEADER_VERSION_OFFSET);
      if (myAttributes.getVersion() != recordsVersion || myContents.getVersion() != recordsVersion) return -1;

      return recordsVersion;
    }

    private long getTimestamp() {
      return myRecords.getLong(HEADER_TIMESTAMP_OFFSET);
    }

    private void setCurrentVersion() {
      myRecords.putInt(HEADER_VERSION_OFFSET, VERSION);
      myRecords.putLong(HEADER_TIMESTAMP_OFFSET, System.currentTimeMillis());
      myAttributes.setVersion(VERSION);
      myContents.setVersion(VERSION);
      myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, SAFELY_CLOSED_MAGIC);
    }

    void cleanRecord(int id) {
      myRecords.put(id * RECORD_SIZE, ZEROES, 0, RECORD_SIZE);
    }

    private PersistentStringEnumerator getNames() {
      return myNames;
    }

    private void closeFiles() throws IOException {
      if (myFlushingFuture != null) {
        myFlushingFuture.cancel(false);
        myFlushingFuture = null;
      }

      if (myNames != null) {
        myNames.close();
        myNames = null;
      }

      if (myAttributes != null) {
        Disposer.dispose(myAttributes);
        myAttributes = null;
      }

      if (myContents != null) {
        Disposer.dispose(myContents);
        myContents = null;
      }

      if (myContentHashesEnumerator != null) {
        myContentHashesEnumerator.close();
        myContentHashesEnumerator = null;
      }

      if (myRecords != null) {
        markClean();
        myRecords.close();
        myRecords = null;
      }
      ourInitialized = false;
    }

    // either called from FlushingDaemon thread under read lock, or from handleError under write lock
    private void markClean() {
      assert myFSRecords.lock.isWriteLocked() || myFSRecords.lock.getReadHoldCount() != 0;
      if (myDirty) {
        myDirty = false;
        // writing here under read lock is safe because no-one else read or write at this offset (except at startup)
        myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, myCorrupted ? CORRUPTED_MAGIC : SAFELY_CLOSED_MAGIC);
      }
    }

    private static final int RESERVED_ATTR_ID = bulkAttrReadSupport ? 1 : 0;
    private static final int FIRST_ATTR_ID_OFFSET = bulkAttrReadSupport ? RESERVED_ATTR_ID : 0;

    private int getAttributeId(@NotNull String attId) throws IOException {
      return myAttributesList.getId(attId) + FIRST_ATTR_ID_OFFSET;
    }

    @Contract("_->fail")
    private void handleError(@NotNull Throwable e) throws RuntimeException, Error {
      assert myFSRecords.lock.getReadHoldCount() == 0;
      if (!myFSRecords.ourIsDisposed) { // No need to forcibly mark VFS corrupted if it is already shut down
        myFSRecords.w.lock(); // lock manually to avoid handleError() recursive calls
        try {
          if (!myCorrupted) {
            createBrokenMarkerFile(e);
            myCorrupted = true;
            doForce();
          }
        }
        finally {
          myFSRecords.w.unlock();
        }
      }

      ExceptionUtil.rethrow(e);
    }

    private static class AttrPageAwareCapacityAllocationPolicy extends CapacityAllocationPolicy {
      boolean myAttrPageRequested;

      @Override
      public int calculateCapacity(int requiredLength) {   // 20% for growth
        return Math.max(myAttrPageRequested ? 8 : 32, Math.min((int)(requiredLength * 1.2), (requiredLength / 1024 + 1) * 1024));
      }
    }
  }

  void connect() {
    myDbConnection.connect();
  }

  public long getCreationTimestamp() {
    return readAndHandleErrors(myDbConnection::getTimestamp);
  }

  private ResizeableMappedFile getRecords() {
    ResizeableMappedFile records = myDbConnection.myRecords;
    assert records != null : "Vfs must be initialized";
    return records;
  }

  private PersistentBTreeEnumerator<byte[]> getContentHashesEnumerator() {
    return myDbConnection.myContentHashesEnumerator;
  }

  private RefCountingStorage getContentStorage() {
    return myDbConnection.myContents;
  }

  private Storage getAttributesStorage() {
    return myDbConnection.myAttributes;
  }

  public PersistentStringEnumerator getNames() {
    return myDbConnection.getNames();
  }

  // todo: Address  / capacity store in records table, size store with payload
  public int createRecord() {
    return writeAndHandleErrors(() -> {
      myDbConnection.markDirty();

      final int free = myDbConnection.getFreeRecord();
      if (free == 0) {
        final int fileLength = length();
        LOG.assertTrue(fileLength % RECORD_SIZE == 0);
        int newRecord = fileLength / RECORD_SIZE;
        myDbConnection.cleanRecord(newRecord);
        assert fileLength + RECORD_SIZE == length();
        return newRecord;
      }
      else {
        if (lazyVfsDataCleaning) deleteContentAndAttributes(free);
        myDbConnection.cleanRecord(free);
        return free;
      }
    });
  }

  private int length() {
    return (int)getRecords().length();
  }

  public int getMaxId() {
    return readAndHandleErrors(() -> length() / RECORD_SIZE);
  }

  void deleteRecordRecursively(int id) {
    writeAndHandleErrors(() -> {
      incModCount(id);
      if (lazyVfsDataCleaning) {
        markAsDeletedRecursively(id);
      }
      else {
        doDeleteRecursively(id);
      }
    });
  }

  private void markAsDeletedRecursively(final int id) {
    for (int subRecord : list(id)) {
      markAsDeletedRecursively(subRecord);
    }

    markAsDeleted(id);
  }

  private void markAsDeleted(final int id) {
    writeAndHandleErrors(() -> {
      myDbConnection.markDirty();
      addToFreeRecordsList(id);
    });
  }

  private void doDeleteRecursively(final int id) {
    for (int subRecord : list(id)) {
      doDeleteRecursively(subRecord);
    }

    deleteRecord(id);
  }

  private void deleteRecord(final int id) {
    writeAndHandleErrors(() -> {
      myDbConnection.markDirty();
      deleteContentAndAttributes(id);

      myDbConnection.cleanRecord(id);
      addToFreeRecordsList(id);
    });
  }

  private void deleteContentAndAttributes(int id) throws IOException {
    int content_page = getContentRecordId(id);
    if (content_page != 0) {
      if (weHaveContentHashes) {
        getContentStorage().releaseRecord(content_page, false);
      }
      else {
        getContentStorage().releaseRecord(content_page);
      }
    }

    int att_page = getAttributeRecordId(id);
    if (att_page != 0) {
      try (final DataInputStream attStream = getAttributesStorage().readStream(att_page)) {
        if (bulkAttrReadSupport) skipRecordHeader(attStream, DbConnection.RESERVED_ATTR_ID, id);

        while (attStream.available() > 0) {
          DataInputOutputUtil.readINT(attStream);// Attribute ID;
          int attAddressOrSize = DataInputOutputUtil.readINT(attStream);

          if (inlineAttributes) {
            if (attAddressOrSize < MAX_SMALL_ATTR_SIZE) {
              attStream.skipBytes(attAddressOrSize);
              continue;
            }
            attAddressOrSize -= MAX_SMALL_ATTR_SIZE;
          }
          getAttributesStorage().deleteRecord(attAddressOrSize);
        }
      }
      getAttributesStorage().deleteRecord(att_page);
    }
  }

  private void addToFreeRecordsList(int id) {
    // DbConnection.addFreeRecord(id); // important! Do not add fileId to free list until restart
    setFlags(id, FREE_RECORD_FLAG, false);
  }

  private static final int ROOT_RECORD_ID = 1;

  @NotNull
  @TestOnly
  int[] listRoots() {
    return readAndHandleErrors(() -> {
      if (ourStoreRootsSeparately) {
        TIntArrayList result = new TIntArrayList();

        try (LineNumberReader stream = new LineNumberReader(
          new BufferedReader(new InputStreamReader(new FileInputStream(myDbConnection.myRootsFile))))) {
          String str;
          while ((str = stream.readLine()) != null) {
            int index = str.indexOf(' ');
            int id = Integer.parseInt(str.substring(0, index));
            result.add(id);
          }
        }
        catch (FileNotFoundException ignored) {
        }

        return result.toNativeArray();
      }

      try (DataInputStream input = readAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        if (input == null) return ArrayUtil.EMPTY_INT_ARRAY;
        final int count = DataInputOutputUtil.readINT(input);
        int[] result = ArrayUtil.newIntArray(count);
        int prevId = 0;
        for (int i = 0; i < count; i++) {
          DataInputOutputUtil.readINT(input); // Name
          prevId = result[i] = DataInputOutputUtil.readINT(input) + prevId; // Id
        }
        return result;
      }
    });
  }

  @TestOnly
  void force() {
    writeAndHandleErrors(myDbConnection::doForce);
  }

  @TestOnly
  boolean isDirty() {
    return readAndHandleErrors(myDbConnection::isDirty);
  }

  private static void saveNameIdSequenceWithDeltas(int[] names, int[] ids, DataOutputStream output) throws IOException {
    DataInputOutputUtil.writeINT(output, names.length);
    int prevId = 0;
    int prevNameId = 0;
    for (int i = 0; i < names.length; i++) {
      DataInputOutputUtil.writeINT(output, names[i] - prevNameId);
      DataInputOutputUtil.writeINT(output, ids[i] - prevId);
      prevId = ids[i];
      prevNameId = names[i];
    }
  }

  int findRootRecord(@NotNull String rootUrl) {
    return writeAndHandleErrors(() -> {
      if (ourStoreRootsSeparately) {
        try (LineNumberReader stream = new LineNumberReader(
          new BufferedReader(new InputStreamReader(new FileInputStream(myDbConnection.myRootsFile))))) {
          String str;
          while ((str = stream.readLine()) != null) {
            int index = str.indexOf(' ');

            if (str.substring(index + 1).equals(rootUrl)) {
              return Integer.parseInt(str.substring(0, index));
            }
          }
        }
        catch (FileNotFoundException ignored) {
        }

        myDbConnection.markDirty();
        try (Writer stream = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(myDbConnection.myRootsFile, true)))) {
          int id = createRecord();
          stream.write(id + " " + rootUrl + "\n");
          return id;
        }
      }

      int root = getNames().tryEnumerate(rootUrl);

      int[] names = ArrayUtil.EMPTY_INT_ARRAY;
      int[] ids = ArrayUtil.EMPTY_INT_ARRAY;
      try (final DataInputStream input = readAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        if (input != null) {
          final int count = DataInputOutputUtil.readINT(input);
          names = ArrayUtil.newIntArray(count);
          ids = ArrayUtil.newIntArray(count);
          int prevId = 0;
          int prevNameId = 0;

          for (int i = 0; i < count; i++) {
            final int name = DataInputOutputUtil.readINT(input) + prevNameId;
            final int id = DataInputOutputUtil.readINT(input) + prevId;
            if (name == root) {
              return id;
            }

            prevNameId = names[i] = name;
            prevId = ids[i] = id;
          }
        }
      }

      myDbConnection.markDirty();
      root = getNames().enumerate(rootUrl);

      int id;
      try (DataOutputStream output = writeAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        id = createRecord();

        int index = Arrays.binarySearch(ids, id);
        ids = ArrayUtil.insert(ids, -index - 1, id);
        names = ArrayUtil.insert(names, -index - 1, root);

        saveNameIdSequenceWithDeltas(names, ids, output);
      }

      return id;
    });
  }

  void deleteRootRecord(int id) {
    writeAndHandleErrors(() -> {
      myDbConnection.markDirty();
      if (ourStoreRootsSeparately) {
        List<String> rootsThatLeft = new ArrayList<>();
        try (LineNumberReader stream = new LineNumberReader(
          new BufferedReader(new InputStreamReader(new FileInputStream(myDbConnection.myRootsFile))))) {
          String str;
          while ((str = stream.readLine()) != null) {
            int index = str.indexOf(' ');
            int rootId = Integer.parseInt(str.substring(0, index));
            if (rootId != id) {
              rootsThatLeft.add(str);
            }
          }
        }
        catch (FileNotFoundException ignored) {
        }

        try (Writer stream = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(myDbConnection.myRootsFile)))) {
          for (String line : rootsThatLeft) {
            stream.write(line);
            stream.write("\n");
          }
        }
        return;
      }

      int[] names;
      int[] ids;
      try (final DataInputStream input = readAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        assert input != null;
        int count = DataInputOutputUtil.readINT(input);

        names = ArrayUtil.newIntArray(count);
        ids = ArrayUtil.newIntArray(count);
        int prevId = 0;
        int prevNameId = 0;
        for (int i = 0; i < count; i++) {
          names[i] = DataInputOutputUtil.readINT(input) + prevNameId;
          ids[i] = DataInputOutputUtil.readINT(input) + prevId;
          prevId = ids[i];
          prevNameId = names[i];
        }
      }

      final int index = ArrayUtil.find(ids, id);
      assert index >= 0;

      names = ArrayUtil.remove(names, index);
      ids = ArrayUtil.remove(ids, index);

      try (DataOutputStream output = writeAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
        saveNameIdSequenceWithDeltas(names, ids, output);
      }
    });
  }

  @NotNull
  int[] list(int id) {
    return readAndHandleErrors(() -> {
      try (final DataInputStream input = readAttribute(id, ourChildrenAttr)) {
        if (input == null) return ArrayUtil.EMPTY_INT_ARRAY;
        final int count = DataInputOutputUtil.readINT(input);
        final int[] result = ArrayUtil.newIntArray(count);
        int prevId = id;
        for (int i = 0; i < count; i++) {
          prevId = result[i] = DataInputOutputUtil.readINT(input) + prevId;
        }
        return result;
      }
    });
  }

  public static class NameId {
    @NotNull
    public static final NameId[] EMPTY_ARRAY = new NameId[0];
    public final int id;
    public final CharSequence name;
    public final int nameId;

    public NameId(int id, int nameId, @NotNull CharSequence name) {
      this.id = id;
      this.nameId = nameId;
      this.name = name;
    }

    @Override
    public String toString() {
      return name + " (" + id + ")";
    }
  }

  @NotNull
  public NameId[] listAll(int parentId) {
    return readAndHandleErrors(() -> {
      try (final DataInputStream input = readAttribute(parentId, ourChildrenAttr)) {
        if (input == null) return NameId.EMPTY_ARRAY;

        int count = DataInputOutputUtil.readINT(input);
        NameId[] result = count == 0 ? NameId.EMPTY_ARRAY : new NameId[count];
        int prevId = parentId;
        for (int i = 0; i < count; i++) {
          int id = DataInputOutputUtil.readINT(input) + prevId;
          prevId = id;
          int nameId = doGetNameId(id);
          result[i] = new NameId(id, nameId, myFileNameCache.getVFileName(nameId, this::doGetNameByNameId));
        }
        return result;
      }
    });
  }

  @NotNull
  public FileNameCache getFileNameCache() {
    return myFileNameCache;
  }

  boolean wereChildrenAccessed(int id) {
    return readAndHandleErrors(() -> findAttributePage(id, ourChildrenAttr, false) != 0);
  }

  private <T> T readAndHandleErrors(@NotNull ThrowableComputable<T, ?> action) {
    assert lock.getReadHoldCount() == 0; // otherwise myDbConnection.handleError(e) (requires write lock) could fail
    try {
      r.lock();
      try {
        return action.compute();
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      myDbConnection.handleError(e);
      throw new RuntimeException(e);
    }
  }

  private <T> T writeAndHandleErrors(@NotNull ThrowableComputable<T, ?> action) {
    try {
      w.lock();
      return action.compute();
    }
    catch (Throwable e) {
      myDbConnection.handleError(e);
      throw new RuntimeException(e);
    }
    finally {
      w.unlock();
    }
  }

  private void writeAndHandleErrors(@NotNull ThrowableRunnable<?> action) {
    try {
      w.lock();
      action.run();
    }
    catch (Throwable e) {
      myDbConnection.handleError(e);
      throw new RuntimeException(e);
    }
    finally {
      w.unlock();
    }
  }


  void updateList(int id, @NotNull int[] childIds) {
    Arrays.sort(childIds);
    writeAndHandleErrors(() -> {
      myDbConnection.markDirty();
      try (DataOutputStream record = writeAttribute(id, ourChildrenAttr)) {
        DataInputOutputUtil.writeINT(record, childIds.length);

        int prevId = id;
        for (int childId : childIds) {
          assert childId > 0 : childId;
          if (childId == id) {
            LOG.error("Cyclic parent child relations");
          }
          else {
            int delta = childId - prevId;
            DataInputOutputUtil.writeINT(record, delta);
            prevId = childId;
          }
        }
      }
    });
  }

  private void incModCount(int id) {
    incLocalModCount();
    final int count = doGetModCount() + 1;
    getRecords().putInt(HEADER_GLOBAL_MOD_COUNT_OFFSET, count);

    setModCount(id, count);
  }

  private void incLocalModCount() {
    myDbConnection.markDirty();
    ourLocalModificationCount++;
  }

  int getLocalModCount() {
    return ourLocalModificationCount; // This is volatile, only modified under Application.runWriteAction() lock.
  }

  int getModCount() {
    return readAndHandleErrors(this::doGetModCount);
  }

  private int doGetModCount() {
    return getRecords().getInt(HEADER_GLOBAL_MOD_COUNT_OFFSET);
  }

  public int getParent(int id) {
    return readAndHandleErrors(() -> {
      final int parentId = getRecordInt(id, PARENT_OFFSET);
      if (parentId == id) {
        LOG.error("Cyclic parent child relations in the database. id = " + id);
        return 0;
      }

      return parentId;
    });
  }

  @Nullable
  VirtualFileSystemEntry findFileById(int id, @NotNull ConcurrentIntObjectMap<VirtualFileSystemEntry> idCache) {
    class ParentFinder implements ThrowableComputable<Void, Throwable> {
      @Nullable private TIntArrayList path;
      private VirtualFileSystemEntry foundParent;

      @Override
      public Void compute() {
        int currentId = id;
        while (true) {
          int parentId = getRecordInt(currentId, PARENT_OFFSET);
          if (parentId == 0) {
            return null;
          }
          if (parentId == currentId || path != null && path.size() % 128 == 0 && path.contains(parentId)) {
            LOG.error("Cyclic parent child relations in the database. id = " + parentId);
            return null;
          }
          foundParent = idCache.get(parentId);
          if (foundParent != null) {
            return null;
          }

          currentId = parentId;
          if (path == null) path = new TIntArrayList();
          path.add(currentId);
        }
      }

      private VirtualFileSystemEntry findDescendantByIdPath() {
        VirtualFileSystemEntry parent = foundParent;
        if (path != null) {
          for (int i = path.size() - 1; i >= 0; i--) {
            parent = findChild(parent, path.get(i));
          }
        }

        return findChild(parent, id);
      }

      private VirtualFileSystemEntry findChild(VirtualFileSystemEntry parent, int childId) {
        if (!(parent instanceof VirtualDirectoryImpl)) {
          return null;
        }
        VirtualFileSystemEntry child = ((VirtualDirectoryImpl)parent).findChildById(childId);
        if (child instanceof VirtualDirectoryImpl) {
          VirtualFileSystemEntry old = idCache.putIfAbsent(childId, child);
          if (old != null) child = old;
        }
        return child;
      }
    }

    ParentFinder finder = new ParentFinder();
    readAndHandleErrors(finder);
    return finder.findDescendantByIdPath();
  }

  void setParent(int id, int parentId) {
    if (id == parentId) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    writeAndHandleErrors(() -> {
      incModCount(id);
      putRecordInt(id, PARENT_OFFSET, parentId);
    });
  }

  int getNameId(int id) {
    return readAndHandleErrors(() -> doGetNameId(id));
  }

  private int doGetNameId(int id) {
    return getRecordInt(id, NAME_OFFSET);
  }

  public int getNameId(String name) {
    return readAndHandleErrors(() -> getNames().enumerate(name));
  }

  public String getName(int id) {
    return getNameSequence(id).toString();
  }

  @NotNull
  CharSequence getNameSequence(int id) {
    return readAndHandleErrors(() -> doGetNameSequence(id));
  }

  @NotNull
  private CharSequence doGetNameSequence(int id) throws IOException {
    final int nameId = getRecordInt(id, NAME_OFFSET);
    return nameId == 0 ? "" : myFileNameCache.getVFileName(nameId, this::doGetNameByNameId);
  }

  public String getNameByNameId(int nameId) {
    return readAndHandleErrors(() -> doGetNameByNameId(nameId));
  }

  private String doGetNameByNameId(int nameId) throws IOException {
    return nameId == 0 ? "" : getNames().valueOf(nameId);
  }

  void setName(int id, @NotNull String name) {
    writeAndHandleErrors(() -> {
      incModCount(id);
      int nameId = getNames().enumerate(name);
      putRecordInt(id, NAME_OFFSET, nameId);
    });
  }

  int getFlags(int id) {
    return readAndHandleErrors(() -> doGetFlags(id));
  }

  private int doGetFlags(int id) {
    return getRecordInt(id, FLAGS_OFFSET);
  }

  void setFlags(int id, int flags, final boolean markAsChange) {
    writeAndHandleErrors(() -> {
      if (markAsChange) {
        incModCount(id);
      }
      putRecordInt(id, FLAGS_OFFSET, flags);
    });
  }

  long getLength(int id) {
    return readAndHandleErrors(() -> getRecords().getLong(getOffset(id, LENGTH_OFFSET)));
  }

  void setLength(int id, long len) {
    writeAndHandleErrors(() -> {
      ResizeableMappedFile records = getRecords();
      int lengthOffset = getOffset(id, LENGTH_OFFSET);
      if (records.getLong(lengthOffset) != len) {
        incModCount(id);
        records.putLong(lengthOffset, len);
      }
    });
  }

  long getTimestamp(int id) {
    return readAndHandleErrors(() -> getRecords().getLong(getOffset(id, TIMESTAMP_OFFSET)));
  }

  void setTimestamp(int id, long value) {
    writeAndHandleErrors(() -> {
      int timeStampOffset = getOffset(id, TIMESTAMP_OFFSET);
      ResizeableMappedFile records = getRecords();
      if (records.getLong(timeStampOffset) != value) {
        incModCount(id);
        records.putLong(timeStampOffset, value);
      }
    });
  }

  int getModCount(int id) {
    return readAndHandleErrors(() -> getRecordInt(id, MOD_COUNT_OFFSET));
  }

  private void setModCount(int id, int value) {
    putRecordInt(id, MOD_COUNT_OFFSET, value);
  }

  private int getContentRecordId(int fileId) {
    return getRecordInt(fileId, CONTENT_OFFSET);
  }

  private void setContentRecordId(int id, int value) {
    putRecordInt(id, CONTENT_OFFSET, value);
  }

  private int getAttributeRecordId(int id) {
    return getRecordInt(id, ATTR_REF_OFFSET);
  }

  private void setAttributeRecordId(int id, int value) {
    putRecordInt(id, ATTR_REF_OFFSET, value);
  }

  private int getRecordInt(int id, int offset) {
    return getRecords().getInt(getOffset(id, offset));
  }

  private void putRecordInt(int id, int offset, int value) {
    getRecords().putInt(getOffset(id, offset), value);
  }

  private static int getOffset(int id, int offset) {
    return id * RECORD_SIZE + offset;
  }

  @Nullable
  DataInputStream readContent(int fileId) {
    int page = readAndHandleErrors(() -> {
      checkFileIsValid(fileId);
      return getContentRecordId(fileId);
    });
    if (page == 0) return null;
    try {
      return doReadContentById(page);
    }
    catch (OutOfMemoryError outOfMemoryError) {
      throw outOfMemoryError;
    }
    catch (Throwable e) {
      myDbConnection.handleError(e);
    }
    return null;
  }

  @NotNull
  DataInputStream readContentById(int contentId) {
    try {
      return doReadContentById(contentId);
    }
    catch (Throwable e) {
      myDbConnection.handleError(e);
    }
    return null;
  }

  @NotNull
  private DataInputStream doReadContentById(int contentId) throws IOException {
    DataInputStream stream = getContentStorage().readStream(contentId);
    if (useCompressionUtil) {
      byte[] bytes = CompressionUtil.readCompressed(stream);
      stream = new DataInputStream(new ByteArrayInputStream(bytes));
    }

    return stream;
  }

  @Nullable
  public DataInputStream readAttributeWithLock(int fileId, FileAttribute att) {
    return readAndHandleErrors(() -> {
      try (DataInputStream stream = readAttribute(fileId, att)) {
        if (stream != null && att.isVersioned()) {
          try {
            int actualVersion = DataInputOutputUtil.readINT(stream);
            if (actualVersion != att.getVersion()) {
              return null;
            }
          }
          catch (IOException e) {
            return null;
          }
        }
        return stream;
      }
    });
  }

  // must be called under r or w lock
  @Nullable
  private DataInputStream readAttribute(int fileId, FileAttribute attribute) throws IOException {
    checkFileIsValid(fileId);

    int recordId = getAttributeRecordId(fileId);
    if (recordId == 0) return null;
    int encodedAttrId = myDbConnection.getAttributeId(attribute.getId());

    Storage storage = getAttributesStorage();

    int page = 0;

    try (DataInputStream attrRefs = storage.readStream(recordId)) {
      if (bulkAttrReadSupport) skipRecordHeader(attrRefs, DbConnection.RESERVED_ATTR_ID, fileId);

      while (attrRefs.available() > 0) {
        final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
        final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

        if (attIdOnPage != encodedAttrId) {
          if (inlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
            attrRefs.skipBytes(attrAddressOrSize);
          }
        }
        else {
          if (inlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
            byte[] b = new byte[attrAddressOrSize];
            attrRefs.readFully(b);
            return new DataInputStream(new ByteArrayInputStream(b));
          }
          page = inlineAttributes ? attrAddressOrSize - MAX_SMALL_ATTR_SIZE : attrAddressOrSize;
          break;
        }
      }
    }

    if (page == 0) {
      return null;
    }
    DataInputStream stream = getAttributesStorage().readStream(page);
    if (bulkAttrReadSupport) skipRecordHeader(stream, encodedAttrId, fileId);
    return stream;
  }

  // Vfs small attrs: store inline:
  // file's AttrId -> [size, capacity] attr record (RESERVED_ATTR_ID fileId)? (attrId ((smallAttrSize smallAttrData) | (attr record)) )
  // other attr record: (AttrId, fileId) ? attrData
  private static final int MAX_SMALL_ATTR_SIZE = 64;

  private int findAttributePage(int fileId, FileAttribute attr, boolean toWrite) throws IOException {
    checkFileIsValid(fileId);

    int recordId = getAttributeRecordId(fileId);
    int encodedAttrId = myDbConnection.getAttributeId(attr.getId());
    boolean directoryRecord = false;

    Storage storage = getAttributesStorage();

    if (recordId == 0) {
      if (!toWrite) return 0;

      recordId = storage.createNewRecord();
      setAttributeRecordId(fileId, recordId);
      directoryRecord = true;
    }
    else {
      try (DataInputStream attrRefs = storage.readStream(recordId)) {
        if (bulkAttrReadSupport) skipRecordHeader(attrRefs, DbConnection.RESERVED_ATTR_ID, fileId);

        while (attrRefs.available() > 0) {
          final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
          final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

          if (attIdOnPage == encodedAttrId) {
            if (inlineAttributes) {
              return attrAddressOrSize < MAX_SMALL_ATTR_SIZE ? -recordId : attrAddressOrSize - MAX_SMALL_ATTR_SIZE;
            }
            else {
              return attrAddressOrSize;
            }
          }
          else {
            if (inlineAttributes && attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
              attrRefs.skipBytes(attrAddressOrSize);
            }
          }
        }
      }
    }

    if (toWrite) {
      try (Storage.AppenderStream appender = storage.appendStream(recordId)) {
        if (bulkAttrReadSupport) {
          if (directoryRecord) {
            DataInputOutputUtil.writeINT(appender, DbConnection.RESERVED_ATTR_ID);
            DataInputOutputUtil.writeINT(appender, fileId);
          }
        }

        DataInputOutputUtil.writeINT(appender, encodedAttrId);
        int attrAddress = storage.createNewRecord();
        DataInputOutputUtil.writeINT(appender, inlineAttributes ? attrAddress + MAX_SMALL_ATTR_SIZE : attrAddress);
        DbConnection.REASONABLY_SMALL.myAttrPageRequested = true;
        return attrAddress;
      }
      finally {
        DbConnection.REASONABLY_SMALL.myAttrPageRequested = false;
      }
    }

    return 0;
  }

  private static void skipRecordHeader(DataInputStream refs, int expectedRecordTag, int expectedFileId) throws IOException {
    int attId = DataInputOutputUtil.readINT(refs);// attrId
    assert attId == expectedRecordTag || expectedRecordTag == 0;
    int fileId = DataInputOutputUtil.readINT(refs);// fileId
    assert expectedFileId == fileId || expectedFileId == 0;
  }

  private static void writeRecordHeader(int recordTag, int fileId, @NotNull DataOutputStream appender) throws IOException {
    DataInputOutputUtil.writeINT(appender, recordTag);
    DataInputOutputUtil.writeINT(appender, fileId);
  }

  private void checkFileIsValid(int fileId) throws IOException {
    assert fileId > 0 : fileId;
    // TODO: This assertion is a bit timey, will remove when bug is caught.
    if (!lazyVfsDataCleaning) {
      assert !BitUtil.isSet(doGetFlags(fileId), FREE_RECORD_FLAG) : "Accessing attribute of a deleted page: " +
                                                                    fileId +
                                                                    ":" +
                                                                    doGetNameSequence(fileId);
    }
  }

  int acquireFileContent(int fileId) {
    return writeAndHandleErrors(() -> {
      int record = getContentRecordId(fileId);
      if (record > 0) getContentStorage().acquireRecord(record);
      return record;
    });
  }

  void releaseContent(int contentId) {
    writeAndHandleErrors(() -> getContentStorage().releaseRecord(contentId, !weHaveContentHashes));
  }

  int getContentId(int fileId) {
    return readAndHandleErrors(() -> getContentRecordId(fileId));
  }

  @NotNull
  DataOutputStream writeContent(int fileId, boolean readOnly) {
    return new ContentOutputStream(fileId, readOnly, this);
  }

  private static final MessageDigest myDigest = ContentHashesUtil.createHashDigest();

  void writeContent(int fileId, ByteArraySequence bytes, boolean readOnly) {
    //noinspection IOResourceOpenedButNotSafelyClosed
    new ContentOutputStream(fileId, readOnly, this).writeBytes(bytes);
  }

  int storeUnlinkedContent(byte[] bytes) {
    return writeAndHandleErrors(() -> {
      int recordId;
      if (weHaveContentHashes) {
        recordId = findOrCreateContentRecord(bytes, 0, bytes.length);
        if (recordId > 0) return recordId;
        recordId = -recordId;
      }
      else {
        recordId = getContentStorage().acquireNewRecord();
      }
      try (AbstractStorage.StorageDataOutput output = getContentStorage().writeStream(recordId, true)) {
        output.write(bytes);
      }
      return recordId;
    });
  }

  @NotNull
  public DataOutputStream writeAttribute(final int fileId, @NotNull FileAttribute att) {
    DataOutputStream stream = new AttributeOutputStream(fileId, att, this);
    if (att.isVersioned()) {
      try {
        DataInputOutputUtil.writeINT(stream, att.getVersion());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return stream;
  }

  private static class ContentOutputStream extends DataOutputStream {
    private final FSRecords myFsRecords;

    final int myFileId;
    final boolean myFixedSize;

    private ContentOutputStream(final int fileId, boolean readOnly, FSRecords records) {
      super(new BufferExposingByteArrayOutputStream());
      myFileId = fileId;
      myFixedSize = readOnly;
      myFsRecords = records;
    }

    @Override
    public void close() throws IOException {
      super.close();

      final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;
      writeBytes(new ByteArraySequence(_out.getInternalBuffer(), 0, _out.size()));
    }

    private void writeBytes(ByteArraySequence bytes) {
      myFsRecords.writeAndHandleErrors(() -> {
        RefCountingStorage contentStorage = myFsRecords.getContentStorage();
        myFsRecords.checkFileIsValid(myFileId);

        int page;
        final boolean fixedSize;
        if (weHaveContentHashes) {
          page = myFsRecords.findOrCreateContentRecord(bytes.getBytes(), bytes.getOffset(), bytes.getLength());

          if (page < 0 || myFsRecords.getContentId(myFileId) != page) {
            myFsRecords.incModCount(myFileId);
            myFsRecords.setContentRecordId(myFileId, page > 0 ? page : -page);
          }

          myFsRecords.setContentRecordId(myFileId, page > 0 ? page : -page);

          if (page > 0) return;
          page = -page;
          fixedSize = true;
        }
        else {
          myFsRecords.incModCount(myFileId);
          page = myFsRecords.getContentRecordId(myFileId);
          if (page == 0 || contentStorage.getRefCount(page) > 1) {
            page = contentStorage.acquireNewRecord();
            myFsRecords.setContentRecordId(myFileId, page);
          }
          fixedSize = myFixedSize;
        }

        ByteArraySequence newBytes;
        if (useCompressionUtil) {
          BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
          try (DataOutputStream outputStream = new DataOutputStream(out)) {
            CompressionUtil.writeCompressed(outputStream, bytes.getBytes(), bytes.getOffset(), bytes.getLength());
          }
          newBytes = new ByteArraySequence(out.getInternalBuffer(), 0, out.size());
        }
        else {
          newBytes = bytes;
        }
        contentStorage.writeBytes(page, newBytes, fixedSize);
      });
    }
  }

  private static final boolean DUMP_STATISTICS = weHaveContentHashes;  // TODO: remove once not needed
  private long totalContents;
  private long totalReuses;
  private long time;
  private int contents;
  private int reuses;

  private int findOrCreateContentRecord(byte[] bytes, int offset, int length) throws IOException {
    assert weHaveContentHashes;

    long started = DUMP_STATISTICS ? System.nanoTime() : 0;
    myDigest.reset();
    myDigest.update(String.valueOf(length - offset).getBytes(Charset.defaultCharset()));
    myDigest.update("\0".getBytes(Charset.defaultCharset()));
    myDigest.update(bytes, offset, length);
    byte[] digest = myDigest.digest();
    long done = DUMP_STATISTICS ? System.nanoTime() - started : 0;
    time += done;

    ++contents;
    totalContents += length;

    if (DUMP_STATISTICS && (contents & 0x3FFF) == 0) {
      LOG.info("Contents:" + contents + " of " + totalContents + ", reuses:" + reuses + " of " + totalReuses + " for " + time / 1000000);
    }
    PersistentBTreeEnumerator<byte[]> hashesEnumerator = getContentHashesEnumerator();
    final int largestId = hashesEnumerator.getLargestId();
    int page = hashesEnumerator.enumerate(digest);

    if (page <= largestId) {
      ++reuses;
      getContentStorage().acquireRecord(page);
      totalReuses += length;

      return page;
    }
    else {
      int newRecord = getContentStorage().acquireNewRecord();
      assert page == newRecord : "Unexpected content storage modification: page=" + page + "; newRecord=" + newRecord;

      return -page;
    }
  }

  private static class AttributeOutputStream extends DataOutputStream {
    private final FSRecords myFsRecords;

    private final FileAttribute myAttribute;
    private final int myFileId;

    private AttributeOutputStream(final int fileId,
                                  @NotNull FileAttribute attribute,
                                  FSRecords records) {
      super(new BufferExposingByteArrayOutputStream());
      myFileId = fileId;
      myAttribute = attribute;
      myFsRecords = records;
    }

    @Override
    public void close() throws IOException {
      super.close();
      myFsRecords.writeAndHandleErrors(() -> {
        final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;

        if (inlineAttributes && _out.size() < MAX_SMALL_ATTR_SIZE) {
          rewriteDirectoryRecordWithAttrContent(_out);
          myFsRecords.incLocalModCount();
        }
        else {
          myFsRecords.incLocalModCount();
          int page = myFsRecords.findAttributePage(myFileId, myAttribute, true);
          if (inlineAttributes && page < 0) {
            rewriteDirectoryRecordWithAttrContent(new BufferExposingByteArrayOutputStream());
            page = myFsRecords.findAttributePage(myFileId, myAttribute, true);
          }

          if (bulkAttrReadSupport) {
            BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream();
            out = stream;
            writeRecordHeader(myFsRecords.myDbConnection.getAttributeId(myAttribute.getId()), myFileId, this);
            write(_out.getInternalBuffer(), 0, _out.size());
            myFsRecords.getAttributesStorage()
                       .writeBytes(page, new ByteArraySequence(stream.getInternalBuffer(), 0, stream.size()), myAttribute.isFixedSize());
          }
          else {
            myFsRecords.getAttributesStorage()
                       .writeBytes(page, new ByteArraySequence(_out.getInternalBuffer(), 0, _out.size()), myAttribute.isFixedSize());
          }
        }
      });
    }

    void rewriteDirectoryRecordWithAttrContent(BufferExposingByteArrayOutputStream _out) throws IOException {
      int recordId = myFsRecords.getAttributeRecordId(myFileId);
      assert inlineAttributes;
      int encodedAttrId = myFsRecords.myDbConnection.getAttributeId(myAttribute.getId());

      Storage storage = myFsRecords.getAttributesStorage();
      BufferExposingByteArrayOutputStream unchangedPreviousDirectoryStream = null;
      boolean directoryRecord = false;


      if (recordId == 0) {
        recordId = storage.createNewRecord();
        myFsRecords.setAttributeRecordId(myFileId, recordId);
        directoryRecord = true;
      }
      else {
        try (DataInputStream attrRefs = storage.readStream(recordId)) {

          DataOutputStream dataStream = null;

          try {
            final int remainingAtStart = attrRefs.available();
            if (bulkAttrReadSupport) {
              unchangedPreviousDirectoryStream = new BufferExposingByteArrayOutputStream();
              dataStream = new DataOutputStream(unchangedPreviousDirectoryStream);
              int attId = DataInputOutputUtil.readINT(attrRefs);
              assert attId == DbConnection.RESERVED_ATTR_ID;
              int fileId = DataInputOutputUtil.readINT(attrRefs);
              assert myFileId == fileId;

              writeRecordHeader(attId, fileId, dataStream);
            }
            while (attrRefs.available() > 0) {
              final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
              final int attrAddressOrSize = DataInputOutputUtil.readINT(attrRefs);

              if (attIdOnPage != encodedAttrId) {
                if (dataStream == null) {
                  unchangedPreviousDirectoryStream = new BufferExposingByteArrayOutputStream();
                  //noinspection IOResourceOpenedButNotSafelyClosed
                  dataStream = new DataOutputStream(unchangedPreviousDirectoryStream);
                }
                DataInputOutputUtil.writeINT(dataStream, attIdOnPage);
                DataInputOutputUtil.writeINT(dataStream, attrAddressOrSize);

                if (attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
                  byte[] b = new byte[attrAddressOrSize];
                  attrRefs.readFully(b);
                  dataStream.write(b);
                }
              }
              else {
                if (attrAddressOrSize < MAX_SMALL_ATTR_SIZE) {
                  if (_out.size() == attrAddressOrSize) {
                    // update in-place when new attr has the same size
                    int remaining = attrRefs.available();
                    storage.replaceBytes(recordId, remainingAtStart - remaining,
                                         new ByteArraySequence(_out.getInternalBuffer(), 0, _out.size()));
                    return;
                  }
                  attrRefs.skipBytes(attrAddressOrSize);
                }
              }
            }
          }
          finally {
            if (dataStream != null) dataStream.close();
          }
        }
      }

      try (AbstractStorage.StorageDataOutput directoryStream = storage.writeStream(recordId)) {
        if (directoryRecord) {
          if (bulkAttrReadSupport) writeRecordHeader(DbConnection.RESERVED_ATTR_ID, myFileId, directoryStream);
        }
        if (unchangedPreviousDirectoryStream != null) {
          directoryStream.write(unchangedPreviousDirectoryStream.getInternalBuffer(), 0, unchangedPreviousDirectoryStream.size());
        }
        if (_out.size() > 0) {
          DataInputOutputUtil.writeINT(directoryStream, encodedAttrId);
          DataInputOutputUtil.writeINT(directoryStream, _out.size());
          directoryStream.write(_out.getInternalBuffer(), 0, _out.size());
        }
      }
    }
  }

  void dispose() {
    writeAndHandleErrors(() -> {
      try {
        myDbConnection.doForce();
        myDbConnection.closeFiles();
      }
      finally {
        ourIsDisposed = true;
      }
    });
  }

  public void invalidateCaches() {
    myDbConnection.createBrokenMarkerFile(null);
  }

  void checkSanity() {
    long t = System.currentTimeMillis();

    int recordCount =
      readAndHandleErrors(() -> {
        final int fileLength = length();
        assert fileLength % RECORD_SIZE == 0;
        return fileLength / RECORD_SIZE;
      });

    IntArrayList usedAttributeRecordIds = new IntArrayList();
    IntArrayList validAttributeIds = new IntArrayList();
    for (int id = 2; id < recordCount; id++) {
      int flags = getFlags(id);
      LOG.assertTrue((flags & ~ALL_VALID_FLAGS) == 0, "Invalid flags: 0x" + Integer.toHexString(flags) + ", id: " + id);
      int currentId = id;
      boolean isFreeRecord = readAndHandleErrors(() -> myDbConnection.myFreeRecords.contains(currentId));
      if (BitUtil.isSet(flags, FREE_RECORD_FLAG)) {
        LOG.assertTrue(isFreeRecord, "Record, marked free, not in free list: " + id);
      }
      else {
        LOG.assertTrue(!isFreeRecord, "Record, not marked free, in free list: " + id);
        checkRecordSanity(id, recordCount, usedAttributeRecordIds, validAttributeIds);
      }
    }

    t = System.currentTimeMillis() - t;
    LOG.info("Sanity check took " + t + " ms");
  }

  private void checkRecordSanity(final int id, final int recordCount, final IntArrayList usedAttributeRecordIds,
                                 final IntArrayList validAttributeIds) {
    int parentId = getParent(id);
    assert parentId >= 0 && parentId < recordCount;
    if (parentId > 0 && getParent(parentId) > 0) {
      int parentFlags = getFlags(parentId);
      assert !BitUtil.isSet(parentFlags, FREE_RECORD_FLAG) : parentId + ": " + Integer.toHexString(parentFlags);
      assert BitUtil.isSet(parentFlags, PersistentFS.IS_DIRECTORY_FLAG) : parentId + ": " + Integer.toHexString(parentFlags);
    }

    CharSequence name = getNameSequence(id);
    LOG.assertTrue(parentId == 0 || name.length() != 0, "File with empty name found under " + getNameSequence(parentId) + ", id=" + id);

    writeAndHandleErrors(() -> {
      checkContentsStorageSanity(id);
      checkAttributesStorageSanity(id, usedAttributeRecordIds, validAttributeIds);
    });

    long length = getLength(id);
    assert length >= -1 : "Invalid file length found for " + name + ": " + length;
  }

  private void checkContentsStorageSanity(int id) {
    int recordId = getContentRecordId(id);
    assert recordId >= 0;
    if (recordId > 0) {
      getContentStorage().checkSanity(recordId);
    }
  }

  private void checkAttributesStorageSanity(int id, IntArrayList usedAttributeRecordIds, IntArrayList validAttributeIds) {
    int attributeRecordId = getAttributeRecordId(id);

    assert attributeRecordId >= 0;
    if (attributeRecordId > 0) {
      try {
        checkAttributesSanity(attributeRecordId, usedAttributeRecordIds, validAttributeIds);
      }
      catch (IOException ex) {
        myDbConnection.handleError(ex);
      }
    }
  }

  private void checkAttributesSanity(final int attributeRecordId, final IntArrayList usedAttributeRecordIds,
                                     final IntArrayList validAttributeIds) throws IOException {
    assert !usedAttributeRecordIds.contains(attributeRecordId);
    usedAttributeRecordIds.add(attributeRecordId);

    try (DataInputStream dataInputStream = getAttributesStorage().readStream(attributeRecordId)) {
      if (bulkAttrReadSupport) skipRecordHeader(dataInputStream, 0, 0);

      while (dataInputStream.available() > 0) {
        int attId = DataInputOutputUtil.readINT(dataInputStream);

        if (!validAttributeIds.contains(attId)) {
          //assert !getNames().valueOf(attId).isEmpty();
          validAttributeIds.add(attId);
        }

        int attDataRecordIdOrSize = DataInputOutputUtil.readINT(dataInputStream);

        if (inlineAttributes) {
          if (attDataRecordIdOrSize < MAX_SMALL_ATTR_SIZE) {
            dataInputStream.skipBytes(attDataRecordIdOrSize);
            continue;
          }
          else {
            attDataRecordIdOrSize -= MAX_SMALL_ATTR_SIZE;
          }
        }
        assert !usedAttributeRecordIds.contains(attDataRecordIdOrSize);
        usedAttributeRecordIds.add(attDataRecordIdOrSize);

        getAttributesStorage().checkSanity(attDataRecordIdOrSize);
      }
    }
  }

  @Contract("_->fail")
  void handleError(Throwable e) throws RuntimeException, Error {
    myDbConnection.handleError(e);
  }
}
