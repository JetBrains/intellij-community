// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.FileSystemInterface;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.containers.ConcurrentPackedBitsArray;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSetQueue;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class FileTypeDetectionService implements Disposable {
  private static final Logger LOG = Logger.getInstance(FileTypeDetectionService.class);

  // cached auto-detected file type. If the file was auto-detected as plain text or binary
  // then the value is null and AUTO_DETECTED_* flags stored in packedFlags are used instead.
  private static final Key<String> DETECTED_FROM_CONTENT_FILE_TYPE_KEY = Key.create("DETECTED_FROM_CONTENT_FILE_TYPE_KEY");

  // these flags are stored in 'packedFlags' as chunks of four bits
  private static final byte AUTO_DETECTED_AS_TEXT_MASK = 1;        // set if the file was auto-detected as text
  private static final byte AUTO_DETECTED_AS_BINARY_MASK = 1<<1;   // set if the file was auto-detected as binary
  // set if auto-detection was performed for this file.
  // if some detector returned some custom file type, it's stored in DETECTED_FROM_CONTENT_FILE_TYPE_KEY file key.
  // otherwise if auto-detected as text or binary, the result is stored in AUTO_DETECTED_AS_TEXT_MASK|AUTO_DETECTED_AS_BINARY_MASK bits
  private static final byte AUTO_DETECT_WAS_RUN_MASK = 1<<2;
  private static final byte ATTRIBUTES_WERE_LOADED_MASK = 1<<3;    // set if AUTO_* bits above were loaded from the file persistent attributes and saved to packedFlags

  private final AtomicInteger counterAutoDetect = new AtomicInteger();
  private final AtomicLong elapsedAutoDetect = new AtomicLong();

  private static final int CHUNK_SIZE = 10;
  private static boolean RE_DETECT_ASYNC = !ApplicationManager.getApplication().isUnitTestMode();
  private final Executor
    reDetectExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("FileTypeManager Redetect Pool",
                                                                            PooledThreadExecutor.INSTANCE,
                                                                            1,
                                                                            this);
  private final HashSetQueue<VirtualFile> filesToRedetect = new HashSetQueue<>();

  private volatile FileAttribute autoDetectedAttribute;
  private final AtomicInteger fileTypeChangedCount;
  private final ConcurrentPackedBitsArray packedFlags = new ConcurrentPackedBitsArray(4);

  private int cachedDetectFileBufferSize = -1;
  private volatile boolean myRestrictCachedDetectedFileTypeAccess;
  private final FileTypeManagerImpl myFileTypeManager;

  FileTypeDetectionService(@NotNull FileTypeManagerImpl fileTypeManager) {
    myFileTypeManager = fileTypeManager;

    int fileTypeChangedCounter = PropertiesComponent.getInstance().getInt("fileTypeChangedCounter", 0);
    fileTypeChangedCount = new AtomicInteger(fileTypeChangedCounter);
    autoDetectedAttribute = new FileAttribute("AUTO_DETECTION_CACHE_ATTRIBUTE", fileTypeChangedCounter, true);

    VirtualFileManager.getInstance().addAsyncFileListener(new AsyncFileListener() {
      @Override
      public @Nullable ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
        Collection<VirtualFile> files = ContainerUtil.map2Set(events, (Function<VFileEvent, VirtualFile>)event -> {
          ProgressManager.checkCanceled();
          VirtualFile file = event instanceof VFileCreateEvent /* avoid expensive find child here */ || isReparseEvent(event) ? null : event.getFile();
          VirtualFile filtered = file != null && wasAutoDetectedBefore(file) && isDetectable(file) ? file : null;
          if (FileTypeManagerImpl.toLog()) {
            FileTypeManagerImpl.log("F: after() VFS event " + event +
                "; filtered file: " + filtered +
                " (file: " + file +
                "; wasAutoDetectedBefore(file): " + (file == null ? null : wasAutoDetectedBefore(file)) +
                "; isDetectable(file): " + (file == null ? null : isDetectable(file)) +
                "; file.getLength(): " + (file == null ? null : file.getLength()) +
                "; file.isValid(): " + (file == null ? null : file.isValid()) +
                "; file.is(VFileProperty.SPECIAL): " + (file == null ? null : file.is(VFileProperty.SPECIAL)) +
                "; packedFlags.get(id): " + (file instanceof VirtualFileWithId ? readableFlags(packedFlags.get(((VirtualFileWithId)file).getId())) : null) +
                "; file.getFileSystem():" + (file == null ? null : file.getFileSystem()) + ")");
          }
          return filtered;
        });
        files.remove(null);
        if (FileTypeManagerImpl.toLog()) {
          FileTypeManagerImpl.log("F: after() VFS events: " + events+"; files: "+files);
        }
        ProgressManager.checkCanceled();
        if (!files.isEmpty() && RE_DETECT_ASYNC) {
          if (FileTypeManagerImpl.toLog()) {
            FileTypeManagerImpl.log("F: after() queued to redetect: " + files);
          }

          for (VirtualFile file : files) {
            ensureReDetected(file);
          }

          if (!files.isEmpty()) {
            return new ChangeApplier() {
              @Override
              public void beforeVfsChange() {
                myRestrictCachedDetectedFileTypeAccess = true;
              }

              @Override
              public void afterVfsChange() {
                try {
                  synchronized (filesToRedetect) {
                    if (filesToRedetect.addAll(files)) {
                      awakeReDetectExecutor();
                    }
                  }
                }
                finally {
                  myRestrictCachedDetectedFileTypeAccess = false;
                }
              }
            };
          }
        }
        return null;
      }

      private boolean isReparseEvent(@NotNull VFileEvent event) {
        return event instanceof VFilePropertyChangeEvent &&
               FileContentUtilCore.FORCE_RELOAD_REQUESTOR.equals(event.getRequestor());
      }
    }, this);

    FileTypeRegistry.FileTypeDetector.EP_NAME.addChangeListener(() -> {
      cachedDetectFileBufferSize = -1;
      clearCaches();
    }, this);

    Application app = ApplicationManager.getApplication();
    Disposer.register(app, this);
  }

  @NotNull
  FileType getOrDetectFromContent(@NotNull VirtualFile file, byte @Nullable [] content) {
    if (!isDetectable(file)) return UnknownFileType.INSTANCE;

    // while vfs events are processing do not access cache, it can be in invalid state;
    if (myRestrictCachedDetectedFileTypeAccess) {
      try {
        return detectFromContent(file, content);
      }
      catch (IOException e) {
        return UnknownFileType.INSTANCE;
      }
    }

    ensureReDetected(file);

    if (file instanceof VirtualFileWithId) {
      int id = ((VirtualFileWithId)file).getId();

      long flags = packedFlags.get(id);
      if (!BitUtil.isSet(flags, ATTRIBUTES_WERE_LOADED_MASK)) {
        flags = readFlagsFromCache(file);
        flags = BitUtil.set(flags, ATTRIBUTES_WERE_LOADED_MASK, true);

        packedFlags.set(id, flags);
        if (FileTypeManagerImpl.toLog()) {
          FileTypeManagerImpl.log("F: getOrDetectFromContent(" + file.getName() + "): readFlagsFromCache() = " + readableFlags(flags));
        }
      }
      boolean autoDetectWasRun = BitUtil.isSet(flags, AUTO_DETECT_WAS_RUN_MASK);
      if (autoDetectWasRun) {
        FileType type = textOrBinaryFromCachedFlags(flags);
        if (FileTypeManagerImpl.toLog()) {
          FileTypeManagerImpl.log("F: getOrDetectFromContent("+file.getName()+"):" +
                                  " cached type = "+(type==null?null:type.getName())+
                                  "; packedFlags.get(id):"+ readableFlags(flags)+
                                  "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): "+file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
        }
        if (type != null) {
          return type;
        }
      }
    }
    FileType fileType = getFileTypeDetectedFromContent(file);
    if (FileTypeManagerImpl.toLog()) {
      FileTypeManagerImpl.log("F: getOrDetectFromContent("+file.getName()+"): " +
                              "getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY) = "+(fileType == null ? null : fileType.getName()));
    }
    if (fileType == null) {
      // run autodetection
      try {
        fileType = detectFromContentAndCache(file, content);
      }
      catch (IOException e) {
        fileType = UnknownFileType.INSTANCE;
      }
    }

    if (FileTypeManagerImpl.toLog()) {
      FileTypeManagerImpl.log("F: getOrDetectFromContent("+file.getName()+"): getFileType after detect run = "+fileType.getName());
    }

    return fileType;
  }

  void loadState(Element state) {
    String fileTypeChangedCounterStr = JDOMExternalizer.readString(state, "fileTypeChangedCounter");
    if (fileTypeChangedCounterStr != null) {
      int fileTypeChangedCounter = StringUtilRt.parseInt(fileTypeChangedCounterStr, 0);
      fileTypeChangedCount.set(fileTypeChangedCounter);
      autoDetectedAttribute = autoDetectedAttribute.newVersion(fileTypeChangedCount.get());
    }
  }

  void clearCaches() {
    packedFlags.clear();
    clearPersistentAttributes();
    if (FileTypeManagerImpl.toLog()) {
      FileTypeManagerImpl.log("F: clearCaches()");
    }
  }

  @Override
  public void dispose() {
    LOG.info(String.format("%s auto-detected files. Detection took %s ms", counterAutoDetect, elapsedAutoDetect));
  }

  static boolean isDetectable(@NotNull final VirtualFile file) {
    if (file.isDirectory() || !file.isValid() || file.is(VFileProperty.SPECIAL) || file.getLength() == 0) {
      // for empty file there is still hope its type will change
      return false;
    }
    return file.getFileSystem() instanceof FileSystemInterface;
  }

  // read auto-detection flags from the persistent FS file attributes. If file attributes are absent, return 0 for flags
  // returns three bits value for AUTO_DETECTED_AS_TEXT_MASK, AUTO_DETECTED_AS_BINARY_MASK and AUTO_DETECT_WAS_RUN_MASK bits
  private byte readFlagsFromCache(@NotNull VirtualFile file) {
    boolean wasAutoDetectRun = false;
    byte status = 0;
    try (DataInputStream stream = autoDetectedAttribute.readAttribute(file)) {
      status = stream == null ? 0 : stream.readByte();
      wasAutoDetectRun = stream != null;
    }
    catch (IOException ignored) {

    }
    status = BitUtil.set(status, AUTO_DETECT_WAS_RUN_MASK, wasAutoDetectRun);

    return (byte)(status & (AUTO_DETECTED_AS_TEXT_MASK | AUTO_DETECTED_AS_BINARY_MASK | AUTO_DETECT_WAS_RUN_MASK));
  }

  // store auto-detection flags to the persistent FS file attributes
  // writes AUTO_DETECTED_AS_TEXT_MASK, AUTO_DETECTED_AS_BINARY_MASK bits only
  private void writeFlagsToCache(@NotNull VirtualFile file, int flags) {
    try (DataOutputStream stream = autoDetectedAttribute.writeAttribute(file)) {
      stream.writeByte(flags & (AUTO_DETECTED_AS_TEXT_MASK | AUTO_DETECTED_AS_BINARY_MASK));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }


  private void clearPersistentAttributes() {
    int count = fileTypeChangedCount.incrementAndGet();
    autoDetectedAttribute = autoDetectedAttribute.newVersion(count);
    PropertiesComponent.getInstance().setValue("fileTypeChangedCounter", Integer.toString(count));
    if (FileTypeManagerImpl.toLog()) {
      FileTypeManagerImpl.log("F: clearPersistentAttributes()");
    }
  }

  private void cacheAutoDetectedFileType(@NotNull VirtualFile file, @NotNull FileType fileType) {
    boolean wasAutodetectedAsText = fileType == PlainTextFileType.INSTANCE;
    boolean wasAutodetectedAsBinary = fileType == UnknownFileType.INSTANCE;

    int flags = BitUtil.set(0, AUTO_DETECTED_AS_TEXT_MASK, wasAutodetectedAsText);
    flags = BitUtil.set(flags, AUTO_DETECTED_AS_BINARY_MASK, wasAutodetectedAsBinary);
    writeFlagsToCache(file, flags);
    if (file instanceof VirtualFileWithId) {
      int id = ((VirtualFileWithId)file).getId();
      flags = BitUtil.set(flags, AUTO_DETECT_WAS_RUN_MASK, true);
      flags = BitUtil.set(flags, ATTRIBUTES_WERE_LOADED_MASK, true);
      packedFlags.set(id, flags);

      if (wasAutodetectedAsText || wasAutodetectedAsBinary) {
        file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, null);
        if (FileTypeManagerImpl.toLog()) {
          FileTypeManagerImpl.log("F: cacheAutoDetectedFileType("+file.getName()+") " +
              "cached to " + fileType.getName() +
              " flags = "+ readableFlags(flags)+
              "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): "+file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
        }
        return;
      }
    }
    file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, fileType.getName());
    if (FileTypeManagerImpl.toLog()) {
      FileTypeManagerImpl.log("F: cacheAutoDetectedFileType("+file.getName()+") " +
          "cached to " + fileType.getName() +
          " flags = "+ readableFlags(flags)+
          "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): "+file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
    }
  }

  private void awakeReDetectExecutor() {
    reDetectExecutor.execute(() -> {
      List<VirtualFile> files = new ArrayList<>(CHUNK_SIZE);
      synchronized (filesToRedetect) {
        for (int i = 0; i < CHUNK_SIZE; i++) {
          VirtualFile file = filesToRedetect.poll();
          if (file == null) break;
          files.add(file);
        }
      }
      if (files.size() == CHUNK_SIZE) {
        awakeReDetectExecutor();
      }
      reDetect(files);
    });
  }

  private void ensureReDetected(@NotNull VirtualFile file) {
    ProgressManager.checkCanceled();
    boolean submitted;
    synchronized (filesToRedetect) {
      submitted = filesToRedetect.remove(file);
    }
    if (submitted) {
      reDetect(Collections.singleton(file));
    }
  }

  @Nullable
  private FileType getFileTypeDetectedFromContent(VirtualFile file) {
    String fileTypeName = file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY);
    return fileTypeName == null ? null : myFileTypeManager.findFileTypeByName(fileTypeName);
  }

  private void reDetect(@NotNull Collection<? extends VirtualFile> files) {
    if (files.isEmpty()) return;
    try {
      List<VirtualFile> changed = new ArrayList<>();
      List<VirtualFile> crashed = new ArrayList<>();
      for (VirtualFile file : files) {
        if (FileTypeManagerImpl.toLog()) {
          FileTypeManagerImpl.log("F: reDetect(" + file.getName() + ") " + file.getName());
        }
        int id = ((VirtualFileWithId)file).getId();
        long flags = packedFlags.get(id);

        FileType before = ObjectUtils.notNull(textOrBinaryFromCachedFlags(flags),
                                              ObjectUtils.notNull(getFileTypeDetectedFromContent(file),
                                                                  PlainTextFileType.INSTANCE));
        FileType after = myFileTypeManager.getByFile(file);

        if (FileTypeManagerImpl.toLog()) {
          FileTypeManagerImpl.log("F: reDetect(" + file.getName() + ") prepare to redetect. flags: " + readableFlags(flags) +
                                  "; beforeType: " + before.getName() + "; afterByFileType: " + (after == null ? null : after.getName()));
        }

        if (after == null || FileTypeManagerImpl.mightBeReplacedByDetectedFileType(after)) {
          try {
            after = detectFromContentAndCache(file, null);
          }
          catch (IOException e) {
            crashed.add(file);
            if (FileTypeManagerImpl.toLog()) {
              FileTypeManagerImpl
                .log("F: reDetect(" + file.getName() + ") " + "before: " + before.getName() + "; after: crashed with " + e.getMessage() +
                     "; now getFileType()=" + file.getFileType().getName() +
                     "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): " + file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
            }
            continue;
          }
        }
        else {
          // back to standard file type
          // detected by conventional methods, no need to run detect-from-content
          file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, null);
          flags = 0;
          packedFlags.set(id, flags);
        }
        if (FileTypeManagerImpl.toLog()) {
          FileTypeManagerImpl.log("F: reDetect(" +
                                  file.getName() +
                                  ") " +
                                  "before: " +
                                  before.getName() +
                                  "; after: " +
                                  after.getName() +
                                  "; now getFileType()=" +
                                  file.getFileType().getName() +
                                  "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): " +
                                  file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
        }

        if (before != after) {
          changed.add(file);
        }
      }
      if (!changed.isEmpty()) {
        reparseLater(changed);
      }
      if (!crashed.isEmpty()) {
        // do not re-scan locked or invalid files too often to avoid constant disk thrashing if that condition is permanent
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> reparseLater(crashed), 10, TimeUnit.SECONDS);
      }
    } catch (ProcessCanceledException e) {
      LOG.error("ProcessCanceledException should not be thrown while re-detection", new Attachment("pce", e));
      throw e;
    }
  }

  private boolean wasAutoDetectedBefore(@NotNull VirtualFile file) {
    if (file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY) != null) {
      return true;
    }
    if (file instanceof VirtualFileWithId) {
      int id = ((VirtualFileWithId)file).getId();
      // do not re-detect binary files
      return (packedFlags.get(id) & (AUTO_DETECT_WAS_RUN_MASK | AUTO_DETECTED_AS_BINARY_MASK)) == AUTO_DETECT_WAS_RUN_MASK;
    }
    return false;
  }

  @NotNull
  private FileType detectFromContentAndCache(@NotNull final VirtualFile file, byte @Nullable [] content) throws IOException {
    long start = System.currentTimeMillis();
    FileType fileType = detectFromContent(file, content);

    cacheAutoDetectedFileType(file, fileType);
    counterAutoDetect.incrementAndGet();
    long elapsed = System.currentTimeMillis() - start;
    elapsedAutoDetect.addAndGet(elapsed);

    return fileType;
  }

  private static int readSafely(@NotNull InputStream stream, byte @NotNull [] buffer, int length) throws IOException {
    int n = stream.read(buffer, 0, length);
    if (n <= 0) {
      // maybe locked because someone else is writing to it
      // repeat inside read action to guarantee all writes are finished
      if (FileTypeManagerImpl.toLog()) {
        FileTypeManagerImpl.log("F: processFirstBytes(): inputStream.read() returned "+n+"; retrying with read action. stream="+ streamInfo(stream));
      }
      n = ReadAction.compute(() -> stream.read(buffer, 0, length));
      if (FileTypeManagerImpl.toLog()) {
        FileTypeManagerImpl.log("F: processFirstBytes(): under read action inputStream.read() returned "+n+"; stream="+ streamInfo(stream));
      }
    }
    return n;
  }

  @NotNull
  private FileType detectFromContent(@NotNull VirtualFile file, byte @Nullable [] content) throws IOException {
    List<FileTypeRegistry.FileTypeDetector> detectors = FileTypeRegistry.FileTypeDetector.EP_NAME.getExtensionList();
    FileType fileType;
    if (content != null) {
      fileType = detect(file, content, content.length, detectors);
    }
    else {
      try (InputStream inputStream = ((FileSystemInterface)file.getFileSystem()).getInputStream(file)) {
        if (FileTypeManagerImpl.toLog()) {
          FileTypeManagerImpl.log("F: detectFromContentAndCache(" + file.getName() + "):" + " inputStream=" + streamInfo(inputStream));
        }

        int fileLength = (int)file.getLength();
        int bufferLength = getDetectFileBufferSize();
        byte[] buffer = fileLength <= FileUtilRt.THREAD_LOCAL_BUFFER_LENGTH
                        ? FileUtilRt.getThreadLocalBuffer()
                        : new byte[Math.min(fileLength, bufferLength)];

        int n = readSafely(inputStream, buffer, buffer.length);
        fileType = detect(file, buffer, n, detectors);

        if (FileTypeManagerImpl.toLog()) {
          try (InputStream newStream = ((FileSystemInterface)file.getFileSystem()).getInputStream(file)) {
            byte[] buffer2 = new byte[50];
            int n2 = newStream.read(buffer2, 0, buffer2.length);
            FileTypeManagerImpl.log("F: detectFromContentAndCache(" + file.getName() + "): result: " + fileType.getName() +
                "; stream: " + streamInfo(inputStream) +
                "; newStream: " + streamInfo(newStream) +
                "; read: " + n2 +
                "; buffer: " + Arrays.toString(buffer2));
          }
        }
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug(file + "; type=" + fileType.getDescription() + "; " + counterAutoDetect);
    }
    return fileType;
  }

  @NotNull
  private FileType detect(@NotNull VirtualFile file, byte @NotNull [] bytes, int length, @NotNull List<? extends FileTypeRegistry.FileTypeDetector> detectors) {
    if (length <= 0) return UnknownFileType.INSTANCE;

    // use PlainTextFileType because it doesn't supply its own charset detector
    // help set charset in the process to avoid double charset detection from content
    return LoadTextUtil.processTextFromBinaryPresentationOrNull(bytes, length,
                                                                file, true, true,
                                                                PlainTextFileType.INSTANCE, (@Nullable CharSequence text) -> {
        if (FileTypeManagerImpl.toLog()) {
          FileTypeManagerImpl.log("F: detectFromContentAndCache.processFirstBytes(" + file.getName() + "): bytes length=" + length +
              "; isText=" + (text != null) + "; text='" + (text == null ? null : StringUtil.first(text, 100, true)) + "'" +
              ", detectors=" + detectors);
        }
        FileType detected = null;
        ByteSequence firstBytes = new ByteArraySequence(bytes, 0, length);
        for (FileTypeRegistry.FileTypeDetector detector : detectors) {
          try {
            detected = detector.detect(file, firstBytes, text);
          }
          catch (ProcessCanceledException e) {
            LOG.error("Detector " + detector + " (" + detector.getClass() + ") threw PCE. Bad detector, bad!", new RuntimeException(e));
          }
          catch (Exception e) {
            LOG.error("Detector " + detector + " (" + detector.getClass() + ") exception occurred:", e);
          }
          if (detected != null) {
            if (FileTypeManagerImpl.toLog()) {
              FileTypeManagerImpl.log("F: detectFromContentAndCache.processFirstBytes(" + file.getName() + "): detector " + detector + " type as " + detected.getName());
            }
            break;
          }
        }

        if (detected == null && text != null) {
          detected = myFileTypeManager.myPatternsTable.findAssociatedFileTypeByHashBang(text);
        }
        if (detected == null) {
          detected = text == null ? UnknownFileType.INSTANCE : PlainTextFileType.INSTANCE;
          if (FileTypeManagerImpl.toLog()) {
            FileTypeManagerImpl.log("F: detectFromContentAndCache.processFirstBytes(" + file.getName() + "): " +
                "no detector was able to detect. assigned " + detected.getName());
          }
        }
        return detected;
      });
  }

  private int getDetectFileBufferSize() {
    int bufferLength = cachedDetectFileBufferSize;
    if (bufferLength == -1) {
      List<FileTypeRegistry.FileTypeDetector> detectors = FileTypeRegistry.FileTypeDetector.EP_NAME.getExtensionList();
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < detectors.size(); i++) {
        FileTypeRegistry.FileTypeDetector detector = detectors.get(i);
        bufferLength = Math.max(bufferLength, detector.getDesiredContentPrefixLength());
      }
      if (bufferLength <= 0) {
        bufferLength = FileUtilRt.getUserContentLoadLimit();
      }
      cachedDetectFileBufferSize = bufferLength;
    }
    return bufferLength;
  }

  @NotNull
  private static String readableFlags(long flags) {
    String result = "";
    if (BitUtil.isSet(flags, ATTRIBUTES_WERE_LOADED_MASK)) result += "ATTRIBUTES_WERE_LOADED_MASK";
    if (BitUtil.isSet(flags, AUTO_DETECT_WAS_RUN_MASK)) result += (result.isEmpty() ? "" :" | ") + "AUTO_DETECT_WAS_RUN_MASK";
    if (BitUtil.isSet(flags, AUTO_DETECTED_AS_BINARY_MASK)) result += (result.isEmpty() ? "" :" | ") + "AUTO_DETECTED_AS_BINARY_MASK";
    if (BitUtil.isSet(flags, AUTO_DETECTED_AS_TEXT_MASK)) result += (result.isEmpty() ? "" :" | ") + "AUTO_DETECTED_AS_TEXT_MASK";
    return result;
  }

  @Nullable //null means the file was not auto-detected as text/binary
  private static FileType textOrBinaryFromCachedFlags(long flags) {
    return BitUtil.isSet(flags, AUTO_DETECTED_AS_TEXT_MASK) ? PlainTextFileType.INSTANCE :
           BitUtil.isSet(flags, AUTO_DETECTED_AS_BINARY_MASK) ? UnknownFileType.INSTANCE :
           null;
  }

  private static void reparseLater(@NotNull List<? extends VirtualFile> changed) {
    ApplicationManager.getApplication().invokeLater(() -> FileContentUtilCore.reparseFiles(changed), ApplicationManager.getApplication().getDisposed());
  }

  // for diagnostics
  @NonNls
  private static Object streamInfo(@NotNull InputStream stream) throws IOException {
    if (stream instanceof BufferedInputStream) {
      InputStream in = ReflectionUtil.getField(stream.getClass(), stream, InputStream.class, "in");
      byte[] buf = ReflectionUtil.getField(stream.getClass(), stream, byte[].class, "buf");
      int count = ReflectionUtil.getField(stream.getClass(), stream, int.class, "count");
      int pos = ReflectionUtil.getField(stream.getClass(), stream, int.class, "pos");
      return "BufferedInputStream(buf=" + (buf == null ? null : Arrays.toString(Arrays.copyOf(buf, count))) +
             ", count=" + count + ", pos=" + pos + ", in=" + streamInfo(in) + ")";
    }
    if (stream instanceof FileInputStream) {
      String path = ReflectionUtil.getField(stream.getClass(), stream, String.class, "path");
      FileChannel channel = ReflectionUtil.getField(stream.getClass(), stream, FileChannel.class, "channel");
      boolean closed = ReflectionUtil.getField(stream.getClass(), stream, boolean.class, "closed");
      int available = stream.available();
      File file = new File(path);
      return "FileInputStream(path=" + path + ", available=" + available + ", closed=" + closed +
             ", channel=" + channel + ", channel.size=" + (channel == null ? null : channel.size()) +
             ", file.exists=" + file.exists() + ", file.content='" + FileUtil.loadFile(file) + "')";
    }
    return stream;
  }

  @TestOnly
  void drainReDetectQueue() {
    try {
      ((BoundedTaskExecutor)reDetectExecutor).waitAllTasksExecuted(1, TimeUnit.MINUTES);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @TestOnly
  @NotNull
  Collection<VirtualFile> dumpReDetectQueue() {
    synchronized (filesToRedetect) {
      return new ArrayList<>(filesToRedetect);
    }
  }

  @TestOnly
  static void reDetectAsync(boolean enable) {
    RE_DETECT_ASYNC = enable;
  }
}
