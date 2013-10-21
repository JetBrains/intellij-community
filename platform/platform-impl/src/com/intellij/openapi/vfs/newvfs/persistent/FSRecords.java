/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.storage.*;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.util.io.IOUtil.deleteAllFilesStartingWith;

@SuppressWarnings({"PointlessArithmeticExpression", "HardCodedStringLiteral"})
public class FSRecords implements Forceable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.vfs.persistent.FSRecords");

  public static final boolean weHaveContentHashes = SystemProperties.getBooleanProperty("idea.share.contents", true);
  private static final int VERSION = 20 + (weHaveContentHashes ? 0x10:0) + (IOUtil.ourByteBuffersUseNativeByteOrder ? 0x37:0);

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

  private static final String CHILDREN_ATT = "FsRecords.DIRECTORY_CHILDREN";

  private static final ReentrantReadWriteLock.ReadLock r;
  private static final ReentrantReadWriteLock.WriteLock w;

  private static volatile int ourLocalModificationCount = 0;
  private static volatile boolean ourIsDisposed;

  private static final int FREE_RECORD_FLAG = 0x100;
  private static final int ALL_VALID_FLAGS = PersistentFS.ALL_VALID_FLAGS | FREE_RECORD_FLAG;

  static {
    //noinspection ConstantConditions
    assert HEADER_SIZE <= RECORD_SIZE;

    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    r = lock.readLock();
    w = lock.writeLock();
  }

  static void writeAttributesToRecord(int id, int parentId, FileAttributes attributes, String name) {
    try {
      w.lock();
      setName(id, name);

      setTimestamp(id, attributes.lastModified);
      setLength(id, attributes.isDirectory() ? -1L : attributes.length);

      setFlags(id, (attributes.isDirectory() ? PersistentFS.IS_DIRECTORY_FLAG : 0) |
                             (attributes.isWritable() ? 0 : PersistentFS.IS_READ_ONLY) |
                             (attributes.isSymLink() ? PersistentFS.IS_SYMLINK : 0) |
                             (attributes.isSpecial() ? PersistentFS.IS_SPECIAL : 0) |
                             (attributes.isHidden() ? PersistentFS.IS_HIDDEN : 0), true);
      setParent(id, parentId);
    } catch (Throwable e) {
      throw DbConnection.handleError(e);
    } finally {
      w.unlock();
    }
  }

  static class DbConnection {
    private static final int SIGNATURE_LENGTH = 20;
    private static boolean ourInitialized;
    private static final ConcurrentHashMap<String, Integer> myAttributeIds = new ConcurrentHashMap<String, Integer>();

    private static PersistentStringEnumerator myNames;
    private static Storage myAttributes;
    private static RefCountingStorage myContents;
    private static ResizeableMappedFile myRecords;
    private static PersistentBTreeEnumerator<byte[]> myContentHashesEnumerator;
    private static final TIntArrayList myFreeRecords = new TIntArrayList();

    private static boolean myDirty = false;
    private static ScheduledFuture<?> myFlushingFuture;
    private static boolean myCorrupted = false;

    private static final AttrPageAwareCapacityAllocationPolicy REASONABLY_SMALL = new AttrPageAwareCapacityAllocationPolicy();


    public static void connect() {
      try {
        w.lock();
        if (!ourInitialized) {
          init();
          setupFlushing();
          ourInitialized = true;
        }
      }
      finally {
        w.unlock();
      }
    }

    private static void scanFreeRecords() {
      final int filelength = (int)myRecords.length();
      LOG.assertTrue(filelength % RECORD_SIZE == 0, "invalid file size: " + filelength);

      int count = filelength / RECORD_SIZE;
      for (int n = 2; n < count; n++) {
        if ((getFlags(n) & FREE_RECORD_FLAG) != 0) {
          myFreeRecords.add(n);
        }
      }
    }

    static int getFreeRecord() {
      if (myFreeRecords.isEmpty()) return 0;
      return myFreeRecords.remove(myFreeRecords.size() - 1);
    }

    private static void createBrokenMarkerFile(@Nullable Throwable reason) {
      final File brokenMarker = getCorruptionMarkerFile();

      try {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(out);
        try {
          new Exception().printStackTrace(stream);
          if (reason != null) {
            stream.print("\nReason:\n");
            reason.printStackTrace(stream);
          }
        }
        finally {
          stream.close();
        }
        LOG.info("Creating VFS corruption marker; Trace=\n" + out.toString());

        final FileWriter writer = new FileWriter(brokenMarker);
        try {
          writer.write("These files are corrupted and must be rebuilt from the scratch on next startup");
        }
        finally {
          writer.close();
        }
      }
      catch (IOException e) {
        // No luck.
      }
    }

    private static File getCorruptionMarkerFile() {
      return new File(basePath(), "corruption.marker");
    }

    private static void init() {
      final File basePath = basePath();
      basePath.mkdirs();

      final File namesFile = new File(basePath, "names.dat");
      final File attributesFile = new File(basePath, "attrib.dat");
      final File contentsFile = new File(basePath, "content.dat");
      final File contentsHashesFile = new File(basePath, "contentHashes.dat");
      final File recordsFile = new File(basePath, "records.dat");

      if (!namesFile.exists()) {
        invalidateIndex();
      }

      try {
        if (getCorruptionMarkerFile().exists()) {
          invalidateIndex();
          throw new IOException("Corruption marker file found");
        }

        PagedFileStorage.StorageLockContext storageLockContext = new PagedFileStorage.StorageLockContext(false);
        myNames = new PersistentStringEnumerator(namesFile, storageLockContext);
        myAttributes = new Storage(attributesFile.getCanonicalPath(), REASONABLY_SMALL);
        myContents = new RefCountingStorage(contentsFile.getCanonicalPath(), CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH); // sources usually zipped with 4x ratio
        myContentHashesEnumerator = weHaveContentHashes ? new PersistentBTreeEnumerator<byte[]>(contentsHashesFile,
                                                                                                new ContentHashesDescriptor(), 4096, storageLockContext) {
          @Override
          protected int doWriteData(byte[] value) throws IOException {
            int record = getContentStorage().createNewRecord();
            int idx = (super.doWriteData(value)) / SIGNATURE_LENGTH;
            if (idx + 1 != record) {
              assert false:"Unexpected content storage modification";
            }
            return idx;
          }

          @Override
          public int getLargestId() {
            return super.getLargestId() / SIGNATURE_LENGTH;
          }

          private boolean myProcessingKeyAtIndex;   // currently protected by w.lock of FSRecords

          @Override
          protected boolean isKeyAtIndex(byte[] value, int idx) throws IOException {
            myProcessingKeyAtIndex = true;
            try {
              return super.isKeyAtIndex(value, addrToIndex(indexToAddr(idx)* SIGNATURE_LENGTH));
            } finally {
              myProcessingKeyAtIndex = false;
            }
          }

          @Override
          public byte[] valueOf(int idx) throws IOException {
            if (myProcessingKeyAtIndex) return super.valueOf(idx);
            return super.valueOf(addrToIndex(indexToAddr(idx)* SIGNATURE_LENGTH));
          }
        }: null;
        boolean aligned = PagedFileStorage.BUFFER_SIZE % RECORD_SIZE == 0;
        assert aligned; // for performance
        myRecords = new ResizeableMappedFile(recordsFile, 20 * 1024, storageLockContext,
                                             PagedFileStorage.BUFFER_SIZE, aligned, IOUtil.ourByteBuffersUseNativeByteOrder);

        if (myRecords.length() == 0) {
          cleanRecord(0); // Clean header
          cleanRecord(1); // Create root record
          setCurrentVersion();
        }

        if (getVersion() != VERSION) {
          throw new IOException("FS repository version mismatch");
        }

        if (myRecords.getInt(HEADER_CONNECTION_STATUS_OFFSET) != SAFELY_CLOSED_MAGIC) {
          throw new IOException("FS repository wasn't safely shut down");
        }
        markDirty();
        scanFreeRecords();
      }
      catch (Exception e) { // IOException, IllegalArgumentException
        LOG.info("Filesystem storage is corrupted or does not exist. [Re]Building. Reason: " + e.getMessage());
        try {
          closeFiles();

          boolean deleted = FileUtil.delete(getCorruptionMarkerFile());
          deleted &= deleteAllFilesStartingWith(namesFile);
          deleted &= AbstractStorage.deleteFiles(attributesFile.getCanonicalPath());
          deleted &= AbstractStorage.deleteFiles(contentsFile.getCanonicalPath());
          deleted &= deleteAllFilesStartingWith(contentsHashesFile);
          deleted &= deleteAllFilesStartingWith(recordsFile);

          if (!deleted) {
            throw new IOException("Cannot delete filesystem storage files");
          }
        }
        catch (final IOException e1) {
          final Runnable warnAndShutdown = new Runnable() {
            @Override
            public void run() {
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
            }
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

    private static void invalidateIndex() {
      LOG.info("Marking VFS as corrupted");
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

    private static File basePath() {
      return new File(getCachesDir());
    }

    private static String getCachesDir() {
      String dir = System.getProperty("caches_dir");
      return dir == null ? PathManager.getSystemPath() + "/caches/" : dir;
    }

    private static void markDirty() {
      if (!myDirty) {
        myDirty = true;
        myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, CONNECTED_MAGIC);
      }
    }

    private static void setupFlushing() {
      myFlushingFuture = FlushingDaemon.everyFiveSeconds(new Runnable() {
        int lastModCount = 0;

        @Override
        public void run() {
          if (lastModCount == ourLocalModificationCount) {
            flushSome();
          }
          lastModCount = ourLocalModificationCount;
        }
      });
    }

    public static void force() {
      try {
        w.lock();
        if (myRecords != null) {
          markClean();
        }
        if (myNames != null) {
          myNames.force();
          myAttributes.force();
          myContents.force();
          if (myContentHashesEnumerator != null) myContentHashesEnumerator.force();
          myRecords.force();
        }
      }
      finally {
        w.unlock();
      }
    }

    public static void flushSome() {
      if (!isDirty() || HeavyProcessLatch.INSTANCE.isRunning()) return;

      try {
        w.lock();
        if (myFlushingFuture == null) {
          return; // avoid NPE when close has already taken place
        }
        myNames.force();

        final boolean attribsFlushed = myAttributes.flushSome();
        final boolean contentsFlushed = myContents.flushSome();
        if (myContentHashesEnumerator != null) myContentHashesEnumerator.force();
        if (attribsFlushed && contentsFlushed) {
          markClean();
          myRecords.force();
        }
      }
      finally {
        w.unlock();
      }
    }

    public static boolean isDirty() {
      return myDirty || myNames.isDirty() || myAttributes.isDirty() || myContents.isDirty() || myRecords.isDirty() ||
             (myContentHashesEnumerator != null ? myContentHashesEnumerator.isDirty() : false);
    }


    private static int getVersion() {
      final int recordsVersion = myRecords.getInt(HEADER_VERSION_OFFSET);
      if (myAttributes.getVersion() != recordsVersion || myContents.getVersion() != recordsVersion) return -1;

      return recordsVersion;
    }

    public static long getTimestamp() {
      return myRecords.getLong(HEADER_TIMESTAMP_OFFSET);
    }

    private static void setCurrentVersion() {
      myRecords.putInt(HEADER_VERSION_OFFSET, VERSION);
      myRecords.putLong(HEADER_TIMESTAMP_OFFSET, System.currentTimeMillis());
      myAttributes.setVersion(VERSION);
      myContents.setVersion(VERSION);
      myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, SAFELY_CLOSED_MAGIC);
    }

    static void cleanRecord(int id) {
      myRecords.put(id * RECORD_SIZE, ZEROES, 0, RECORD_SIZE);
    }

    public static PersistentStringEnumerator getNames() {
      return myNames;
    }

    private static void closeFiles() throws IOException {
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

    private static void markClean() {
      if (myDirty) {
        myDirty = false;
        myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, myCorrupted ? CORRUPTED_MAGIC : SAFELY_CLOSED_MAGIC);
      }
    }

    private static int getAttributeId(@NotNull String attId) throws IOException {
      Integer integer = myAttributeIds.get(attId);
      if (integer != null) return integer.intValue();
      int enumeratedId = myNames.enumerate(attId);
      integer = myAttributeIds.putIfAbsent(attId, enumeratedId);
      return integer == null ? enumeratedId:  integer.intValue();
    }

    private static RuntimeException handleError(final Throwable e) {
      if (!ourIsDisposed) {
        // No need to forcibly mark VFS corrupted if it is already shut down
        if (!myCorrupted) {
          createBrokenMarkerFile(e);
          myCorrupted = true;
          force();
        }
      }

      return new RuntimeException(e);
    }

    private static class AttrPageAwareCapacityAllocationPolicy extends CapacityAllocationPolicy {
      boolean myAttrPageRequested;

      @Override
      public int calculateCapacity(int requiredLength) {   // 20% for growth
        return Math.max(myAttrPageRequested ? 8:32, Math.min((int)(requiredLength * 1.2), (requiredLength / 1024 + 1) * 1024));
      }
    }

    private static class ContentHashesDescriptor implements KeyDescriptor<byte[]>, DifferentSerializableBytesImplyNonEqualityPolicy {
      @Override
      public void save(DataOutput out, byte[] value) throws IOException {
        out.write(value);
      }

      @Override
      public byte[] read(DataInput in) throws IOException {
        byte[] b = new byte[SIGNATURE_LENGTH];
        in.readFully(b);
        return b;
      }

      @Override
      public int getHashCode(byte[] value) {
        int hash = 0; // take first 4 bytes, this should be good enough hash given we reference git revisions with 7-8 hex digits
        for (int i = 0; i < 4; ++i) {
          hash = (hash << 8) + (value[i] & 0xFF);
        }
        return hash;
      }

      @Override
      public boolean isEqual(byte[] val1, byte[] val2) {
        return Arrays.equals(val1, val2);
      }
    }
  }

  public FSRecords() {
  }

  public static void connect() {
    DbConnection.connect();
  }

  public static long getCreationTimestamp() {
    try {
      r.lock();
      return DbConnection.getTimestamp();
    }
    finally {
      r.unlock();
    }
  }

  private static ResizeableMappedFile getRecords() {
    return DbConnection.myRecords;
  }

  private static PersistentBTreeEnumerator<byte[]> getContentHashesEnumerator() {
    return DbConnection.myContentHashesEnumerator;
  }

  private static RefCountingStorage getContentStorage() {
    return DbConnection.myContents;
  }

  private static Storage getAttributesStorage() {
    return DbConnection.myAttributes;
  }

  public static PersistentStringEnumerator getNames() {
    return DbConnection.getNames();
  }

  public static int createRecord() {
    try {
      w.lock();
      DbConnection.markDirty();

      final int free = DbConnection.getFreeRecord();
      if (free == 0) {
        final int fileLength = (int)getRecords().length();
        LOG.assertTrue(fileLength % RECORD_SIZE == 0);
        int newRecord = fileLength / RECORD_SIZE;
        DbConnection.cleanRecord(newRecord);
        assert fileLength + RECORD_SIZE == getRecords().length();
        return newRecord;
      }
      else {
        DbConnection.cleanRecord(free);
        return free;
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  static void deleteRecordRecursively(int id) {
    try {
      w.lock();
      incModCount(id);
      doDeleteRecursively(id);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  private static void doDeleteRecursively(final int id) {
    for (int subrecord : list(id)) {
      doDeleteRecursively(subrecord);
    }

    deleteRecord(id);
  }

  private static void deleteRecord(final int id) {
    try {
      w.lock();
      DbConnection.markDirty();
      deleteContentAndAttributes(id);

      DbConnection.cleanRecord(id);
      addToFreeRecordsList(id);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  private static void deleteContentAndAttributes(int id) throws IOException {
    int content_page = getContentRecordId(id);
    if (content_page != 0) {
      if (weHaveContentHashes) {
        getContentStorage().releaseRecord(content_page, false);
      } else {
        getContentStorage().releaseRecord(content_page);
      }
    }

    int att_page = getAttributeRecordId(id);
    if (att_page != 0) {
      final DataInputStream attStream = getAttributesStorage().readStream(att_page);
      while (attStream.available() > 0) {
        DataInputOutputUtil.readINT(attStream); // Attribute ID;
        int attAddress = DataInputOutputUtil.readINT(attStream);
        getAttributesStorage().deleteRecord(attAddress);
      }
      attStream.close();
      getAttributesStorage().deleteRecord(att_page);
    }
  }

  private static void addToFreeRecordsList(int id) {
    // DbConnection.addFreeRecord(id); // do not add fileId to free list until restart
    setFlags(id, FREE_RECORD_FLAG, false);
  }

  static int[] listRoots() {
    try {
      try {
        r.lock();
        final DataInputStream input = readAttribute(1, CHILDREN_ATT);
        if (input == null) return ArrayUtil.EMPTY_INT_ARRAY;

        try {
          final int count = DataInputOutputUtil.readINT(input);
          int[] result = ArrayUtil.newIntArray(count);
          for (int i = 0; i < count; i++) {
            DataInputOutputUtil.readINT(input); // Name
            result[i] = DataInputOutputUtil.readINT(input); // Id
          }
          return result;
        }
        finally {
          input.close();
        }
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  @Override
  public void force() {
    DbConnection.force();
  }

  @Override
  public boolean isDirty() {
    return DbConnection.isDirty();
  }

  public static int findRootRecord(@NotNull String rootUrl) {
    try {
      try {
        w.lock();
        DbConnection.markDirty();
        final int root = getNames().enumerate(rootUrl);

        final DataInputStream input = readAttribute(1, CHILDREN_ATT);
        int[] names = ArrayUtil.EMPTY_INT_ARRAY;
        int[] ids = ArrayUtil.EMPTY_INT_ARRAY;

        if (input != null) {
          try {
            final int count = DataInputOutputUtil.readINT(input);
            names = ArrayUtil.newIntArray(count);
            ids = ArrayUtil.newIntArray(count);
            for (int i = 0; i < count; i++) {
              final int name = DataInputOutputUtil.readINT(input);
              final int id = DataInputOutputUtil.readINT(input);
              if (name == root) {
                return id;
              }

              names[i] = name;
              ids[i] = id;
            }
          }
          finally {
            input.close();
          }
        }

        final DataOutputStream output = writeAttribute(1, CHILDREN_ATT, false);
        int id;
        try {
          id = createRecord();
          DataInputOutputUtil.writeINT(output, names.length + 1);
          for (int i = 0; i < names.length; i++) {
            DataInputOutputUtil.writeINT(output, names[i]);
            DataInputOutputUtil.writeINT(output, ids[i]);
          }
          DataInputOutputUtil.writeINT(output, root);
          DataInputOutputUtil.writeINT(output, id);
        }
        finally {
          output.close();
        }

        return id;
      }
      finally {
        w.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static void deleteRootRecord(int id) {
    try {
      try {
        w.lock();
        DbConnection.markDirty();
        final DataInputStream input = readAttribute(1, CHILDREN_ATT);
        assert input != null;
        int count;
        int[] names;
        int[] ids;
        try {
          count = DataInputOutputUtil.readINT(input);

          names = ArrayUtil.newIntArray(count);
          ids = ArrayUtil.newIntArray(count);
          for (int i = 0; i < count; i++) {
            names[i] = DataInputOutputUtil.readINT(input);
            ids[i] = DataInputOutputUtil.readINT(input);
          }
        }
        finally {
          input.close();
        }

        final int index = ArrayUtil.find(ids, id);
        assert index >= 0;

        names = ArrayUtil.remove(names, index);
        ids = ArrayUtil.remove(ids, index);

        final DataOutputStream output = writeAttribute(1, CHILDREN_ATT, false);
        try {
          DataInputOutputUtil.writeINT(output, count - 1);
          for (int i = 0; i < names.length; i++) {
            DataInputOutputUtil.writeINT(output, names[i]);
            DataInputOutputUtil.writeINT(output, ids[i]);
          }
        }
        finally {
          output.close();
        }
      }
      finally {
        w.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static int[] list(int id) {
    try {
      r.lock();
      try {
        final DataInputStream input = readAttribute(id, CHILDREN_ATT);
        if (input == null) return ArrayUtil.EMPTY_INT_ARRAY;

        final int count = DataInputOutputUtil.readINT(input);
        final int[] result = ArrayUtil.newIntArray(count);
        for (int i = 0; i < count; i++) {
          int childId = DataInputOutputUtil.readINT(input);
          childId = childId >= 0 ? childId + id : -childId;
          result[i] = childId;
        }
        input.close();
        return result;
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static class NameId {
    public static final NameId[] EMPTY_ARRAY = new NameId[0];
    public final int id;
    public final String name;

    public NameId(int id, @NotNull String name) {
      this.id = id;
      this.name = name;
    }

    @Override
    public String toString() {
      return name + " (" + id + ")";
    }
  }

  @NotNull
  public static NameId[] listAll(int parentId) {
    try {
      r.lock();
      try {
        final DataInputStream input = readAttribute(parentId, CHILDREN_ATT);
        if (input == null) return NameId.EMPTY_ARRAY;

        int count = DataInputOutputUtil.readINT(input);
        NameId[] result = count == 0 ? NameId.EMPTY_ARRAY : new NameId[count];
        for (int i = 0; i < count; i++) {
          int id = DataInputOutputUtil.readINT(input);
          id = id >= 0 ? id + parentId : -id;
          result[i] = new NameId(id, getName(id));
        }
        input.close();
        return result;
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static boolean wereChildrenAccessed(int id) {
    try {
      r.lock();
      try {
        return findAttributePage(id, CHILDREN_ATT, false) != 0;
      } finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static void updateList(int id, @NotNull int[] children) {
    try {
      w.lock();
      DbConnection.markDirty();
      final DataOutputStream record = writeAttribute(id, CHILDREN_ATT, false);
      DataInputOutputUtil.writeINT(record, children.length);
      for (int child : children) {
        if (child == id) {
          LOG.error("Cyclic parent child relations");
        }
        else {
          child = child > id ? child - id : -child;
          DataInputOutputUtil.writeINT(record, child);
        }
      }
      record.close();
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  private static void incModCount(int id) {
    DbConnection.markDirty();
    ourLocalModificationCount++;
    final int count = getModCount() + 1;
    getRecords().putInt(HEADER_GLOBAL_MOD_COUNT_OFFSET, count);

    int parent = id;
    int depth = 10000;
    while (parent != 0) {
      setModCount(parent, count);
      parent = getParent(parent);
      if (depth -- == 0) {
        LOG.error("Cyclic parent child relation? file: " + getName(id));
        return;
      }
    }
  }

  public static int getLocalModCount() {
    return ourLocalModificationCount; // This is volatile, only modified under Application.runWriteAction() lock.
  }

  public static int getModCount() {
    try {
      r.lock();
      return getRecords().getInt(HEADER_GLOBAL_MOD_COUNT_OFFSET);
    }
    finally {
      r.unlock();
    }
  }

  public static int getParent(int id) {
    try {
      r.lock();
      try {
        final int parentId = getRecordInt(id, PARENT_OFFSET);
        if (parentId == id) {
          LOG.error("Cyclic parent child relations in the database. id = " + id);
          return 0;
        }

        return parentId;
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static void setParent(int id, int parent) {
    if (id == parent) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    try {
      w.lock();
      incModCount(id);
      putRecordInt(id, PARENT_OFFSET, parent);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static int getNameId(int id) {
    try {
      r.lock();
      try {
        return getRecordInt(id, NAME_OFFSET);
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static int getNameId(String name) {
    try {
      r.lock();
      try {
        return getNames().enumerate(name);
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static String getName(int id) {
    try {
      r.lock();
      try {
        final int nameId = getRecordInt(id, NAME_OFFSET);
        return nameId != 0 ? getNames().valueOf(nameId) : "";
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static String getNameByNameId(int nameId) {
    try {
      r.lock();
      try {
        return nameId != 0 ? getNames().valueOf(nameId) : "";
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  public static void setName(int id, String name) {
    try {
      w.lock();
      incModCount(id);
      putRecordInt(id, NAME_OFFSET, getNames().enumerate(name));
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static int getFlags(int id) {
    try {
      r.lock();
      return getRecordInt(id, FLAGS_OFFSET);
    }
    finally {
      r.unlock();
    }
  }

  public static void setFlags(int id, int flags, final boolean markAsChange) {
    try {
      w.lock();
      if (markAsChange) {
        incModCount(id);
      }
      putRecordInt(id, FLAGS_OFFSET, flags);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static long getLength(int id) {
    try {
      r.lock();
      return getRecords().getLong(getOffset(id, LENGTH_OFFSET));
    }
    finally {
      r.unlock();
    }
  }

  public static void setLength(int id, long len) {
    try {
      w.lock();
      incModCount(id);
      getRecords().putLong(getOffset(id, LENGTH_OFFSET), len);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static long getTimestamp(int id) {
    try {
      r.lock();
      return getRecords().getLong(getOffset(id, TIMESTAMP_OFFSET));
    }
    finally {
      r.unlock();
    }
  }

  public static void setTimestamp(int id, long value) {
    try {
      w.lock();
      incModCount(id);
      getRecords().putLong(getOffset(id, TIMESTAMP_OFFSET), value);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static int getModCount(int id) {
    try {
      r.lock();
      return getRecordInt(id, MOD_COUNT_OFFSET);
    }
    finally {
      r.unlock();
    }
  }

  private static void setModCount(int id, int value) {
    putRecordInt(id, MOD_COUNT_OFFSET, value);
  }

  private static int getContentRecordId(int fileId) {
    return getRecordInt(fileId, CONTENT_OFFSET);
  }

  private static void setContentRecordId(int id, int value) {
    putRecordInt(id, CONTENT_OFFSET, value);
  }

  private static int getAttributeRecordId(int id) {
    return getRecordInt(id, ATTR_REF_OFFSET);
  }

  private static void setAttributeRecordId(int id, int value) {
    putRecordInt(id, ATTR_REF_OFFSET, value);
  }

  private static int getRecordInt(int id, int offset) {
    return getRecords().getInt(getOffset(id, offset));
  }

  private static void putRecordInt(int id, int offset, int value) {
    getRecords().putInt(getOffset(id, offset), value);
  }

  private static int getOffset(int id, int offset) {
    return id * RECORD_SIZE + offset;
  }

  @Nullable
  public static DataInputStream readContent(int fileId) {
    try {
      int page;
      try {
        r.lock();
        checkFileIsValid(fileId);

        page = getContentRecordId(fileId);
        if (page == 0) return null;
      }
      finally {
        r.unlock();
      }
      return getContentStorage().readStream(page);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  @Nullable
  public static DataInputStream readContentById(int contentId) {
    try {
      return getContentStorage().readStream(contentId);
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  @Nullable
  static DataInputStream readAttributeWithLock(int fileId, String attId) {
    try {
      synchronized (attId) {
        try {
          r.lock();
          return readAttribute(fileId, attId);
        }
        finally {
          r.unlock();
        }
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  // should be called under r or w lock
  @Nullable
  private static DataInputStream readAttribute(int fileId, String attId) throws IOException {
    int page = findAttributePage(fileId, attId, false);
    if (page == 0) return null;
    return getAttributesStorage().readStream(page);
  }

  private static int findAttributePage(int fileId, @NotNull String attrId, boolean toWrite) throws IOException {
    checkFileIsValid(fileId);

    Storage storage = getAttributesStorage();

    int encodedAttrId = DbConnection.getAttributeId(attrId);
    int recordId = getAttributeRecordId(fileId);

    if (recordId == 0) {
      if (!toWrite) return 0;

      recordId = storage.createNewRecord();
      setAttributeRecordId(fileId, recordId);
    }
    else {
      DataInputStream attrRefs = storage.readStream(recordId);
      try {
        while (attrRefs.available() > 0) {
          final int attIdOnPage = DataInputOutputUtil.readINT(attrRefs);
          final int attrAddress = DataInputOutputUtil.readINT(attrRefs);

          if (attIdOnPage == encodedAttrId) return attrAddress;
        }
      }
      finally {
        attrRefs.close();
      }
    }

    if (toWrite) {
      Storage.AppenderStream appender = storage.appendStream(recordId);
      DataInputOutputUtil.writeINT(appender, encodedAttrId);
      int attrAddress = storage.createNewRecord();
      DataInputOutputUtil.writeINT(appender, attrAddress);
      DbConnection.REASONABLY_SMALL.myAttrPageRequested = true;
      try {
        appender.close();
      } finally {
        DbConnection.REASONABLY_SMALL.myAttrPageRequested = false;
      }
      return attrAddress;
    }

    return 0;
  }

  private static void checkFileIsValid(int fileId) {
    assert fileId > 0 : fileId;
    // TODO: This assertion is a bit timey, will remove when bug is caught.
    assert (getFlags(fileId) & FREE_RECORD_FLAG) == 0 : "Accessing attribute of a deleted page: " + fileId + ":" + getName(fileId);
  }

  public static int acquireFileContent(int fileId) {
    try {
      w.lock();
      int record = getContentRecordId(fileId);
      if (record > 0) getContentStorage().acquireRecord(record);
      return record;
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      w.unlock();
    }
  }

  public static void releaseContent(int contentId) {
    try {
      w.lock();
      RefCountingStorage contentStorage = getContentStorage();
      if (weHaveContentHashes) {
        contentStorage.releaseRecord(contentId, false);
      } else {
        contentStorage.releaseRecord(contentId);
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    } finally {
      w.unlock();
    }
  }

  public static int getContentId(int fileId) {
    try {
      r.lock();
      try {
        return getContentRecordId(fileId);
      }
      finally {
        r.unlock();
      }
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
  }

  @NotNull
  public static DataOutputStream writeContent(int fileId, boolean readOnly) {
    return new ContentOutputStream(fileId, readOnly);
  }

  private static final MessageDigest myDigest;

  static {
    MessageDigest digest;
    try {
      digest = weHaveContentHashes ? MessageDigest.getInstance("SHA1") : null;
    } catch (NoSuchAlgorithmException ex) {
      assert false:"Every Java implementation should have SHA-1 support"; // http://docs.oracle.com/javase/7/docs/api/java/security/MessageDigest.html
      digest = null;
    }
    myDigest = digest;
  }

  public static void writeContent(int fileId, ByteSequence bytes, boolean readOnly) throws IOException {
    new ContentOutputStream(fileId, readOnly).writeBytes(bytes);
  }

  public static int storeUnlinkedContent(byte[] bytes) {
    try {
      w.lock();
      int recordId;

      if (weHaveContentHashes) {
        recordId = findOrCreateContentRecord(bytes, 0, bytes.length);
        if (recordId > 0) return recordId;
        recordId = -recordId;
      } else {
        recordId = getContentStorage().acquireNewRecord();
      }
      AbstractStorage.StorageDataOutput output = getContentStorage().writeStream(recordId, true);
      output.write(bytes);
      output.close();
      return recordId;
    }
    catch (IOException e) {
      throw DbConnection.handleError(e);
    } finally {
      w.unlock();
    }
  }

  @NotNull
  public static DataOutputStream writeAttribute(final int fileId, @NotNull String attId, boolean fixedSize) {
    return new AttributeOutputStream(fileId, attId, fixedSize);
  }

  private static class ContentOutputStream extends DataOutputStream {
    protected final int myFileId;
    protected final boolean myFixedSize;

    private ContentOutputStream(final int fileId, boolean readOnly) {
      super(new BufferExposingByteArrayOutputStream());
      myFileId = fileId;
      myFixedSize = readOnly;
    }

    @Override
    public void close() throws IOException {
      super.close();

      try {
        final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;
        writeBytes(new ByteSequence(_out.getInternalBuffer(), 0, _out.size()));
      }
      catch (Throwable e) {
        throw DbConnection.handleError(e);
      }
    }

    public void writeBytes(ByteSequence bytes) throws IOException {
      int page;
      RefCountingStorage contentStorage = getContentStorage();
      final boolean fixedSize;
      try {
        w.lock();
        incModCount(myFileId);

        checkFileIsValid(myFileId);

        if (weHaveContentHashes) {
          page = findOrCreateContentRecord(bytes.getBytes(), bytes.getOffset(), bytes.getLength());

          incModCount(myFileId);
          checkFileIsValid(myFileId);

          setContentRecordId(myFileId, page > 0 ? page : -page);

          if (page > 0) return;
          page = -page;
          fixedSize = true;
        } else {
          page = getContentRecordId(myFileId);
          if (page == 0 || contentStorage.getRefCount(page) > 1) {
            page = contentStorage.acquireNewRecord();
            setContentRecordId(myFileId, page);
          }
          fixedSize = myFixedSize;
        }
      }
      finally {
        w.unlock();
      }

      contentStorage.writeBytes(page, bytes, fixedSize);
    }
  }

  private static final boolean DO_HARD_CONSISTENCY_CHECK = false;
  private static final boolean DUMP_STATISTICS = weHaveContentHashes;  // TODO: remove once not needed
  private static long totalContents, totalReuses, time;
  private static int contents, reuses;

  private static int findOrCreateContentRecord(byte[] bytes, int offset, int length) throws IOException {
    assert weHaveContentHashes;
    byte[] digest;

    long started = DUMP_STATISTICS ? System.nanoTime():0;
    myDigest.reset();
    myDigest.update(String.valueOf(length - offset).getBytes(Charset.defaultCharset()));
    myDigest.update("\0".getBytes(Charset.defaultCharset()));
    myDigest.update(bytes, offset, length);
    digest = myDigest.digest();
    long done = DUMP_STATISTICS ? System.nanoTime() - started : 0;
    time += done;

    ++contents;
    totalContents += length;

    if (DUMP_STATISTICS && (contents & 0x3FFF) == 0) {
      LOG.info("Contents:"+contents + " of " + totalContents + ", reuses:"+reuses + " of " + totalReuses + " for " + (time / 1000000));
    }
    PersistentBTreeEnumerator<byte[]> hashesEnumerator = getContentHashesEnumerator();
    final int largestId = hashesEnumerator.getLargestId();
    int page = hashesEnumerator.enumerate(digest);
    getContentStorage().acquireRecord(page);

    if (page < largestId) {
      ++reuses;
      totalReuses += length;

      if (DO_HARD_CONSISTENCY_CHECK) {
        DataInputStream stream = getContentStorage().readStream(page);
        int i = offset;
        for(int c = 0; c < length; ++c) {
          if (stream.available() == 0) {
            assert false;
          }
          if (bytes[i++] != stream.readByte()) {
            assert false;
          }
        }
        if (stream.available() > 0) {
          assert false;
        }
      }
      return page;
    }

    if (DO_HARD_CONSISTENCY_CHECK) {
      if (hashesEnumerator.enumerate(digest) != page) {
        assert false;
      }

      byte[] bytes1 = hashesEnumerator.valueOf(page);
      if (!Arrays.equals(digest, bytes1)) {
        assert false;
      }
    }

    return -page;
  }

  private static class AttributeOutputStream extends DataOutputStream {
    private final String myAttributeId;
    private final int myFileId;
    private final boolean myFixedSize;

    private AttributeOutputStream(final int fileId, @NotNull String attributeId, boolean fixedSize) {
      super(new BufferExposingByteArrayOutputStream());
      myFileId = fileId;
      myFixedSize = fixedSize;
      myAttributeId = attributeId;
    }

    @Override
    public void close() throws IOException {
      super.close();

      try {
        synchronized (myAttributeId) {
          final BufferExposingByteArrayOutputStream _out = (BufferExposingByteArrayOutputStream)out;
          final int page;
          try {
            w.lock();
            incModCount(myFileId);
            page = findAttributePage(myFileId, myAttributeId, true);
          }
          finally {
            w.unlock();
          }
          getAttributesStorage().writeBytes(page, new ByteSequence(_out.getInternalBuffer(), 0, _out.size()), myFixedSize);
        }
      }
      catch (Throwable e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public static void dispose() {
    try {
      w.lock();
      DbConnection.force();
      DbConnection.closeFiles();
    }
    catch (Throwable e) {
      throw DbConnection.handleError(e);
    }
    finally {
      ourIsDisposed = true;
      w.unlock();
    }
  }

  public static void invalidateCaches() {
    DbConnection.createBrokenMarkerFile(null);
  }

  public static void checkSanity() {
    long t = System.currentTimeMillis();

    try {
      r.lock();
      final int fileLength = (int)getRecords().length();
      assert fileLength % RECORD_SIZE == 0;
      int recordCount = fileLength / RECORD_SIZE;

      IntArrayList usedAttributeRecordIds = new IntArrayList();
      IntArrayList validAttributeIds = new IntArrayList();
      for (int id = 2; id < recordCount; id++) {
        int flags = getFlags(id);
        LOG.assertTrue((flags & ~ALL_VALID_FLAGS) == 0, "Invalid flags: 0x" + Integer.toHexString(flags) + ", id: " + id);
        if ((flags & FREE_RECORD_FLAG) != 0) {
          LOG.assertTrue(DbConnection.myFreeRecords.contains(id), "Record, marked free, not in free list: " + id);
        }
        else {
          LOG.assertTrue(!DbConnection.myFreeRecords.contains(id), "Record, not marked free, in free list: " + id);
          checkRecordSanity(id, recordCount, usedAttributeRecordIds, validAttributeIds);
        }
      }
    }
    finally {
      r.unlock();
    }

    t = System.currentTimeMillis() - t;
    LOG.info("Sanity check took " + t + " ms");
  }

  private static void checkRecordSanity(final int id, final int recordCount, final IntArrayList usedAttributeRecordIds,
                                        final IntArrayList validAttributeIds) {
    int parentId = getParent(id);
    assert parentId >= 0 && parentId < recordCount;
    if (parentId > 0 && getParent(parentId) > 0) {
      int parentFlags = getFlags(parentId);
      assert (parentFlags & FREE_RECORD_FLAG) == 0 : parentId + ": "+Integer.toHexString(parentFlags);
      assert (parentFlags & PersistentFS.IS_DIRECTORY_FLAG) != 0 : parentId + ": "+Integer.toHexString(parentFlags);
    }

    String name = getName(id);
    LOG.assertTrue(parentId == 0 || !name.isEmpty(), "File with empty name found under " + getName(parentId) + ", id=" + id);

    checkContentsStorageSanity(id);
    checkAttributesStorageSanity(id, usedAttributeRecordIds, validAttributeIds);

    long length = getLength(id);
    assert length >= -1 : "Invalid file length found for " + name + ": " + length;
  }

  private static void checkContentsStorageSanity(int id) {
    int recordId = getContentRecordId(id);
    assert recordId >= 0;
    if (recordId > 0) {
      getContentStorage().checkSanity(recordId);
    }
  }

  private static void checkAttributesStorageSanity(int id, IntArrayList usedAttributeRecordIds, IntArrayList validAttributeIds) {
    int attributeRecordId = getAttributeRecordId(id);

    assert attributeRecordId >= 0;
    if (attributeRecordId > 0) {
      try {
        checkAttributesSanity(attributeRecordId, usedAttributeRecordIds, validAttributeIds);
      }
      catch (IOException ex) {
        throw DbConnection.handleError(ex);
      }
    }
  }

  private static void checkAttributesSanity(final int attributeRecordId, final IntArrayList usedAttributeRecordIds,
                                            final IntArrayList validAttributeIds) throws IOException {
    assert !usedAttributeRecordIds.contains(attributeRecordId);
    usedAttributeRecordIds.add(attributeRecordId);

    final DataInputStream dataInputStream = getAttributesStorage().readStream(attributeRecordId);
    try {
      while(dataInputStream.available() > 0) {
        int attId = DataInputOutputUtil.readINT(dataInputStream);
        int attDataRecordId = DataInputOutputUtil.readINT(dataInputStream);
        assert !usedAttributeRecordIds.contains(attDataRecordId);
        usedAttributeRecordIds.add(attDataRecordId);
        if (!validAttributeIds.contains(attId)) {
          assert !getNames().valueOf(attId).isEmpty();
          validAttributeIds.add(attId);
        }
        getAttributesStorage().checkSanity(attDataRecordId);
      }
    }
    finally {
      dataInputStream.close();
    }
  }

  public static RuntimeException handleError(Throwable e) {
    return DbConnection.handleError(e);
  }
}
