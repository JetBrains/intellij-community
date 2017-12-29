// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.*;
import com.intellij.openapi.options.NonLazySchemeProcessor;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.FileSystemInterface;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.containers.ConcurrentPackedBitsArray;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSetQueue;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.*;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@State(name = "FileTypeManager", storages = @Storage("filetypes.xml"), additionalExportFile = FileTypeManagerImpl.FILE_SPEC )
public class FileTypeManagerImpl extends FileTypeManagerEx implements PersistentStateComponent<Element>, Disposable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(FileTypeManagerImpl.class);

  // You must update all existing default configurations accordingly
  private static final int VERSION = 17;
  private static final ThreadLocal<Pair<VirtualFile, FileType>> FILE_TYPE_FIXED_TEMPORARILY = new ThreadLocal<>();

  // cached auto-detected file type. If the file was auto-detected as plain text or binary
  // then the value is null and AUTO_DETECTED_* flags stored in packedFlags are used instead.
  static final Key<FileType> DETECTED_FROM_CONTENT_FILE_TYPE_KEY = Key.create("DETECTED_FROM_CONTENT_FILE_TYPE_KEY");

  // must be sorted
  private static final String DEFAULT_IGNORED = "*.hprof;*.pyc;*.pyo;*.rbc;*.yarb;*~;.DS_Store;.git;.hg;.svn;CVS;__pycache__;_svn;vssver.scc;vssver2.scc;";
  static {
    List<String> strings = StringUtil.split(DEFAULT_IGNORED, ";");
    for (int i = 0; i < strings.size(); i++) {
      String string = strings.get(i);
      String prev = i==0?"":strings.get(i-1);
      assert prev.compareTo(string) < 0 : "DEFAULT_IGNORED must be sorted, but got: '"+prev+"' >= '"+string+"'";
    }
  }

  private static boolean RE_DETECT_ASYNC = !ApplicationManager.getApplication().isUnitTestMode();
  private final Set<FileType> myDefaultTypes = new THashSet<>();
  private FileTypeIdentifiableByVirtualFile[] mySpecialFileTypes = FileTypeIdentifiableByVirtualFile.EMPTY_ARRAY;

  private FileTypeAssocTable<FileType> myPatternsTable = new FileTypeAssocTable<>();
  private final IgnoredPatternSet myIgnoredPatterns = new IgnoredPatternSet();
  private final IgnoredFileCache myIgnoredFileCache = new IgnoredFileCache(myIgnoredPatterns);

  private final FileTypeAssocTable<FileType> myInitialAssociations = new FileTypeAssocTable<>();
  private final Map<FileNameMatcher, String> myUnresolvedMappings = new THashMap<>();
  private final Map<FileNameMatcher, Trinity<String, String, Boolean>> myUnresolvedRemovedMappings = new THashMap<>();
  /** This will contain removed mappings with "approved" states */
  private final Map<FileNameMatcher, Pair<FileType, Boolean>> myRemovedMappings = new THashMap<>();

  @NonNls private static final String ELEMENT_FILETYPE = "filetype";
  @NonNls private static final String ELEMENT_IGNORE_FILES = "ignoreFiles";
  @NonNls private static final String ATTRIBUTE_LIST = "list";

  @NonNls private static final String ATTRIBUTE_VERSION = "version";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_DESCRIPTION = "description";

  private static class StandardFileType {
    @NotNull private final FileType fileType;
    @NotNull private final List<FileNameMatcher> matchers;

    private StandardFileType(@NotNull FileType fileType, @NotNull List<FileNameMatcher> matchers) {
      this.fileType = fileType;
      this.matchers = matchers;
    }
  }

  private final MessageBus myMessageBus;
  private final Map<String, StandardFileType> myStandardFileTypes = new LinkedHashMap<>();
  @NonNls
  private static final String[] FILE_TYPES_WITH_PREDEFINED_EXTENSIONS = {"JSP", "JSPX", "DTD", "HTML", "Properties", "XHTML"};
  private final SchemeManager<FileType> mySchemeManager;
  @NonNls
  static final String FILE_SPEC = "filetypes";

  // these flags are stored in 'packedFlags' as chunks of four bits
  private static final byte AUTO_DETECTED_AS_TEXT_MASK = 1;        // set if the file was auto-detected as text
  private static final byte AUTO_DETECTED_AS_BINARY_MASK = 1<<1;   // set if the file was auto-detected as binary

  // set if auto-detection was performed for this file.
  // if some detector returned some custom file type, it's stored in DETECTED_FROM_CONTENT_FILE_TYPE_KEY file key.
  // otherwise if auto-detected as text or binary, the result is stored in AUTO_DETECTED_AS_TEXT_MASK|AUTO_DETECTED_AS_BINARY_MASK bits
  private static final byte AUTO_DETECT_WAS_RUN_MASK = 1<<2;
  private static final byte ATTRIBUTES_WERE_LOADED_MASK = 1<<3;    // set if AUTO_* bits above were loaded from the file persistent attributes and saved to packedFlags
  private final ConcurrentPackedBitsArray packedFlags = new ConcurrentPackedBitsArray(4);

  private final AtomicInteger counterAutoDetect = new AtomicInteger();
  private final AtomicLong elapsedAutoDetect = new AtomicLong();

  public FileTypeManagerImpl(MessageBus bus, SchemeManagerFactory schemeManagerFactory, PropertiesComponent propertiesComponent) {
    int fileTypeChangedCounter = StringUtilRt.parseInt(propertiesComponent.getValue("fileTypeChangedCounter"), 0);
    fileTypeChangedCount = new AtomicInteger(fileTypeChangedCounter);
    autoDetectedAttribute = new FileAttribute("AUTO_DETECTION_CACHE_ATTRIBUTE", fileTypeChangedCounter, true);

    myMessageBus = bus;
    mySchemeManager = schemeManagerFactory.create(FILE_SPEC, new NonLazySchemeProcessor<FileType, AbstractFileType>() {
      @NotNull
      @Override
      public AbstractFileType readScheme(@NotNull Element element, boolean duringLoad) {
        if (!duringLoad) {
          fireBeforeFileTypesChanged();
        }
        AbstractFileType type = (AbstractFileType)loadFileType(element, false);
        if (!duringLoad) {
          fireFileTypesChanged();
        }
        return type;
      }

      @NotNull
      @Override
      public SchemeState getState(@NotNull FileType fileType) {
        if (!(fileType instanceof AbstractFileType) || !shouldSave(fileType)) {
          return SchemeState.NON_PERSISTENT;
        }
        if (!myDefaultTypes.contains(fileType)) {
          return SchemeState.POSSIBLY_CHANGED;
        }
        return ((AbstractFileType)fileType).isModified() ? SchemeState.POSSIBLY_CHANGED : SchemeState.NON_PERSISTENT;
      }

      @NotNull
      @Override
      public Element writeScheme(@NotNull AbstractFileType fileType) {
        Element root = new Element(ELEMENT_FILETYPE);

        root.setAttribute("binary", String.valueOf(fileType.isBinary()));
        if (!StringUtil.isEmpty(fileType.getDefaultExtension())) {
          root.setAttribute("default_extension", fileType.getDefaultExtension());
        }
        root.setAttribute(ATTRIBUTE_DESCRIPTION, fileType.getDescription());
        root.setAttribute(ATTRIBUTE_NAME, fileType.getName());

        fileType.writeExternal(root);

        Element map = new Element(AbstractFileType.ELEMENT_EXTENSION_MAP);
        writeExtensionsMap(map, fileType, false);
        if (!map.getChildren().isEmpty()) {
          root.addContent(map);
        }
        return root;
      }

      @Override
      public void onSchemeDeleted(@NotNull AbstractFileType scheme) {
        GuiUtils.invokeLaterIfNeeded(() -> {
          Application app = ApplicationManager.getApplication();
          app.runWriteAction(() -> fireBeforeFileTypesChanged());
          myPatternsTable.removeAllAssociations(scheme);
          app.runWriteAction(() -> fireFileTypesChanged());
        }, ModalityState.NON_MODAL);
      }
    });
    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        Collection<VirtualFile> files = ContainerUtil.map2Set(events, (Function<VFileEvent, VirtualFile>)event -> {
          VirtualFile file = event instanceof VFileCreateEvent ? /* avoid expensive find child here */ null : event.getFile();
          VirtualFile filtered = file != null && wasAutoDetectedBefore(file) && isDetectable(file) ? file : null;
          if (toLog()) {
            log("F: after() VFS event " + event +
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
        if (toLog()) {
          log("F: after() VFS events: " + events+"; files: "+files);
        }
        if (!files.isEmpty() && RE_DETECT_ASYNC) {
          if (toLog()) {
            log("F: after() queued to redetect: " + files);
          }

          synchronized (filesToRedetect) {
            if (filesToRedetect.addAll(files)) {
              awakeReDetectExecutor();
            }
          }
        }
      }
    });

    //noinspection SpellCheckingInspection
    myIgnoredPatterns.setIgnoreMasks(DEFAULT_IGNORED);

    // this should be done BEFORE reading state
    initStandardFileTypes();
  }

  @VisibleForTesting
  void initStandardFileTypes() {
    FileTypeConsumer consumer = new FileTypeConsumer() {
      @Override
      public void consume(@NotNull FileType fileType) {
        register(fileType, parse(fileType.getDefaultExtension()));
      }

      @Override
      public void consume(@NotNull final FileType fileType, String extensions) {
        register(fileType, parse(extensions));
      }

      @Override
      public void consume(@NotNull final FileType fileType, @NotNull final FileNameMatcher... matchers) {
        register(fileType, new ArrayList<>(Arrays.asList(matchers)));
      }

      @Override
      public FileType getStandardFileTypeByName(@NotNull final String name) {
        final StandardFileType type = myStandardFileTypes.get(name);
        return type != null ? type.fileType : null;
      }

      private void register(@NotNull FileType fileType, @NotNull List<FileNameMatcher> fileNameMatchers) {
        final StandardFileType type = myStandardFileTypes.get(fileType.getName());
        if (type != null) {
          type.matchers.addAll(fileNameMatchers);
        }
        else {
          myStandardFileTypes.put(fileType.getName(), new StandardFileType(fileType, fileNameMatchers));
        }
      }
    };

    for (FileTypeFactory factory : FileTypeFactory.FILE_TYPE_FACTORY_EP.getExtensions()) {
      try {
        factory.createFileTypes(consumer);
      }
      catch (Throwable e) {
        PluginManager.handleComponentError(e, factory.getClass().getName(), null);
      }
    }
    for (StandardFileType pair : myStandardFileTypes.values()) {
      registerFileTypeWithoutNotification(pair.fileType, pair.matchers, true);
    }

    if (PlatformUtils.isDatabaseIDE() || PlatformUtils.isCidr()) {
      // build scripts are correct, but it is required to run from sources
      return;
    }

    try {
      URL defaultFileTypesUrl = FileTypeManagerImpl.class.getResource("/defaultFileTypes.xml");
      if (defaultFileTypesUrl != null) {
        Element defaultFileTypesElement = JdomKt.loadElement(URLUtil.openStream(defaultFileTypesUrl));
        for (Element e : defaultFileTypesElement.getChildren()) {
          if ("filetypes".equals(e.getName())) {
            for (Element element : e.getChildren(ELEMENT_FILETYPE)) {
              loadFileType(element, true);
            }
          }
          else if (AbstractFileType.ELEMENT_EXTENSION_MAP.equals(e.getName())) {
            readGlobalMappings(e, true);
          }
        }

        if (PlatformUtils.isIdeaCommunity()) {
          Element extensionMap = new Element(AbstractFileType.ELEMENT_EXTENSION_MAP);
          extensionMap.addContent(new Element(AbstractFileType.ELEMENT_MAPPING)
                                    .setAttribute(AbstractFileType.ATTRIBUTE_EXT, "jspx")
                                    .setAttribute(AbstractFileType.ATTRIBUTE_TYPE, "XML"));
          //noinspection SpellCheckingInspection
          extensionMap.addContent(new Element(AbstractFileType.ELEMENT_MAPPING)
                                    .setAttribute(AbstractFileType.ATTRIBUTE_EXT, "tagx")
                                    .setAttribute(AbstractFileType.ATTRIBUTE_TYPE, "XML"));
          readGlobalMappings(extensionMap, true);
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  boolean toLog;
  private boolean toLog() {
    return toLog;
  }

  private static void log(String message) {
    LOG.debug(message + " - "+Thread.currentThread());
  }

  private final BoundedTaskExecutor reDetectExecutor = new BoundedTaskExecutor("FileTypeManager redetect pool", PooledThreadExecutor.INSTANCE, 1, this);
  private final HashSetQueue<VirtualFile> filesToRedetect = new HashSetQueue<>();

  private static final int CHUNK_SIZE = 10;
  private void awakeReDetectExecutor() {
    reDetectExecutor.submit(() -> {
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

  @TestOnly
  public void drainReDetectQueue() {
    try {
      reDetectExecutor.waitAllTasksExecuted(1, TimeUnit.MINUTES);
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

  private void reDetect(@NotNull Collection<VirtualFile> files) {
    List<VirtualFile> changed = new ArrayList<>();
    List<VirtualFile> crashed = new ArrayList<>();
    for (VirtualFile file : files) {
      boolean shouldRedetect = wasAutoDetectedBefore(file) && isDetectable(file);
      if (toLog()) {
        log("F: reDetect("+file.getName()+") " + file.getName() + "; shouldRedetect: " + shouldRedetect);
      }
      if (shouldRedetect) {
        int id = ((VirtualFileWithId)file).getId();
        long flags = packedFlags.get(id);
        FileType before = ObjectUtils.notNull(textOrBinaryFromCachedFlags(flags),
                                              ObjectUtils.notNull(file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY),
                                                                  PlainTextFileType.INSTANCE));
        FileType after = getByFile(file);

        if (toLog()) {
          log("F: reDetect(" + file.getName() + ") prepare to redetect. flags: " + readableFlags(flags) +
              "; beforeType: " + before.getName() + "; afterByFileType: " + (after == null ? null : after.getName()));
        }

        if (after == null) {
          try {
            after = detectFromContentAndCache(file);
          }
          catch (IOException e) {
            crashed.add(file);
            if (toLog()) {
              log("F: reDetect(" + file.getName() + ") " + "before: " + before.getName() + "; after: crashed with " + e.getMessage() +
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
        if (toLog()) {
          log("F: reDetect(" + file.getName() + ") " + "before: " + before.getName() + "; after: " + after.getName() +
              "; now getFileType()=" + file.getFileType().getName() +
              "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): " + file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
        }

        if (before != after) {
          changed.add(file);
        }
      }
    }
    if (!changed.isEmpty()) {
      reparseLater(changed);
    }
    if (!crashed.isEmpty()) {
      // do not re-scan locked or invalid files too often to avoid constant disk thrashing if that condition is permanent
      AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> reparseLater(crashed), 10, TimeUnit.SECONDS);
    }
  }

  private static void reparseLater(@NotNull List<VirtualFile> changed) {
    ApplicationManager.getApplication().invokeLater(() -> FileContentUtilCore.reparseFiles(changed), ApplicationManager.getApplication().getDisposed());
  }

  private boolean wasAutoDetectedBefore(@NotNull VirtualFile file) {
    if (file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY) != null) {
      return true;
    }
    if (file instanceof VirtualFileWithId) {
      int id = Math.abs(((VirtualFileWithId)file).getId());
      // do not re-detect binary files
      return (packedFlags.get(id) & (AUTO_DETECT_WAS_RUN_MASK | AUTO_DETECTED_AS_BINARY_MASK)) == AUTO_DETECT_WAS_RUN_MASK;
    }
    return false;
  }

  @Override
  @NotNull
  public FileType getStdFileType(@NotNull @NonNls String name) {
    StandardFileType stdFileType = myStandardFileTypes.get(name);
    return stdFileType != null ? stdFileType.fileType : PlainTextFileType.INSTANCE;
  }

  @Override
  public void initComponent() {
    if (!myUnresolvedMappings.isEmpty()) {
      for (StandardFileType pair : myStandardFileTypes.values()) {
        registerReDetectedMappings(pair);
      }
    }
    // Resolve unresolved mappings initialized before certain plugin initialized.
    for (StandardFileType pair : myStandardFileTypes.values()) {
      bindUnresolvedMappings(pair.fileType);
    }

    boolean isAtLeastOneStandardFileTypeHasBeenRead = false;
    for (FileType fileType : mySchemeManager.loadSchemes()) {
      isAtLeastOneStandardFileTypeHasBeenRead |= myInitialAssociations.hasAssociationsFor(fileType);
    }
    if (isAtLeastOneStandardFileTypeHasBeenRead) {
      restoreStandardFileExtensions();
    }
  }

  @Override
  @NotNull
  public FileType getFileTypeByFileName(@NotNull String fileName) {
    return getFileTypeByFileName((CharSequence)fileName);
  }

  @Override
  @NotNull
  public FileType getFileTypeByFileName(@NotNull CharSequence fileName) {
    FileType type = myPatternsTable.findAssociatedFileType(fileName);
    return ObjectUtils.notNull(type, UnknownFileType.INSTANCE);
  }

  public void freezeFileTypeTemporarilyIn(@NotNull VirtualFile file, @NotNull Runnable runnable) {
    FileType fileType = file.getFileType();
    Pair<VirtualFile, FileType> old = FILE_TYPE_FIXED_TEMPORARILY.get();
    FILE_TYPE_FIXED_TEMPORARILY.set(Pair.create(file, fileType));
    if (toLog()) {
      log("F: freezeFileTypeTemporarilyIn(" + file.getName() + ") to " + fileType.getName()+" in "+Thread.currentThread());
    }
    try {
      runnable.run();
    }
    finally {
      if (old == null) {
        FILE_TYPE_FIXED_TEMPORARILY.remove();
      }
      else {
        FILE_TYPE_FIXED_TEMPORARILY.set(old);
      }
      if (toLog()) {
        log("F: unfreezeFileType(" + file.getName() + ") in "+Thread.currentThread());
      }
    }
  }

  @Override
  @NotNull
  public FileType getFileTypeByFile(@NotNull VirtualFile file) {
    FileType fileType = getByFile(file);

    if (fileType == null) {
      fileType = file instanceof StubVirtualFile ? UnknownFileType.INSTANCE : getOrDetectFromContent(file);
    }

    return fileType;
  }

  @Nullable // null means all conventional detect methods returned UnknownFileType.INSTANCE, have to detect from content
  public FileType getByFile(@NotNull VirtualFile file) {
    Pair<VirtualFile, FileType> fixedType = FILE_TYPE_FIXED_TEMPORARILY.get();
    if (fixedType != null && fixedType.getFirst().equals(file)) {
      FileType fileType = fixedType.getSecond();
      if (toLog()) {
        log("F: getByFile(" + file.getName() + ") was frozen to " + fileType.getName()+" in "+Thread.currentThread());
      }
      return fileType;
    }

    if (file instanceof LightVirtualFile) {
      FileType fileType = ((LightVirtualFile)file).getAssignedFileType();
      if (fileType != null) {
        return fileType;
      }
    }

    for (FileTypeIdentifiableByVirtualFile type : mySpecialFileTypes) {
      if (type.isMyFileType(file)) {
        if (toLog()) {
          log("F: getByFile(" + file.getName() + "): Special file type: " + type.getName());
        }
        return type;
      }
    }

    FileType fileType = getFileTypeByFileName(file.getNameSequence());
    if (fileType == UnknownFileType.INSTANCE) {
      fileType = null;
    }
    if (toLog()) {
      log("F: getByFile(" + file.getName() + ") By name file type: "+(fileType == null ? null : fileType.getName()));
    }
    return fileType;
  }

  @NotNull
  private FileType getOrDetectFromContent(@NotNull VirtualFile file) {
    if (!isDetectable(file)) return UnknownFileType.INSTANCE;
    if (file instanceof VirtualFileWithId) {
      int id = ((VirtualFileWithId)file).getId();
      if (id < 0) return UnknownFileType.INSTANCE;

      long flags = packedFlags.get(id);
      if (!BitUtil.isSet(flags, ATTRIBUTES_WERE_LOADED_MASK)) {
        flags = readFlagsFromCache(file);
        flags = BitUtil.set(flags, ATTRIBUTES_WERE_LOADED_MASK, true);

        packedFlags.set(id, flags);
        if (toLog()) {
          log("F: getOrDetectFromContent(" + file.getName() + "): readFlagsFromCache() = " + readableFlags(flags));
        }
      }
      boolean autoDetectWasRun = BitUtil.isSet(flags, AUTO_DETECT_WAS_RUN_MASK);
      if (autoDetectWasRun) {
        FileType type = textOrBinaryFromCachedFlags(flags);
        if (toLog()) {
          log("F: getOrDetectFromContent("+file.getName()+"):" +
              " cached type = "+(type==null?null:type.getName())+
              "; packedFlags.get(id):"+ readableFlags(flags)+
              "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): "+file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
        }
        if (type != null) {
          return type;
        }
      }
    }
    FileType fileType = file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY);
    if (toLog()) {
      log("F: getOrDetectFromContent("+file.getName()+"): " +
          "getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY) = "+(fileType == null ? null : fileType.getName()));
    }
    if (fileType == null) {
      // run autodetection
      try {
        fileType = detectFromContentAndCache(file);
      }
      catch (IOException e) {
        fileType = UnknownFileType.INSTANCE;
      }
    }

    if (toLog()) {
      log("F: getOrDetectFromContent("+file.getName()+"): getFileType after detect run = "+fileType.getName());
    }

    return fileType;
  }

  private static String readableFlags(long flags) {
    String result = "";
    if (BitUtil.isSet(flags, ATTRIBUTES_WERE_LOADED_MASK)) result += (result.isEmpty() ? "" :" | ") + "ATTRIBUTES_WERE_LOADED_MASK";
    if (BitUtil.isSet(flags, AUTO_DETECT_WAS_RUN_MASK)) result += (result.isEmpty() ? "" :" | ") + "AUTO_DETECT_WAS_RUN_MASK";
    if (BitUtil.isSet(flags, AUTO_DETECTED_AS_BINARY_MASK)) result += (result.isEmpty() ? "" :" | ") + "AUTO_DETECTED_AS_BINARY_MASK";
    if (BitUtil.isSet(flags, AUTO_DETECTED_AS_TEXT_MASK)) result += (result.isEmpty() ? "" :" | ") + "AUTO_DETECTED_AS_TEXT_MASK";
    return result;
  }

  private volatile FileAttribute autoDetectedAttribute;
  // read auto-detection flags from the persistent FS file attributes. If file attributes are absent, return 0 for flags
  // returns three bits value for AUTO_DETECTED_AS_TEXT_MASK, AUTO_DETECTED_AS_BINARY_MASK and AUTO_DETECT_WAS_RUN_MASK bits
  // protected for Upsource
  protected byte readFlagsFromCache(@NotNull VirtualFile file) {
    DataInputStream stream = autoDetectedAttribute.readAttribute(file);
    boolean wasAutoDetectRun = false;
    byte status = 0;
    try {
      try {
        status = stream == null ? 0 : stream.readByte();
        wasAutoDetectRun = stream != null;
      }
      finally {
        if (stream != null) {
          stream.close();
        }
      }
    }
    catch (IOException ignored) {
    }
    status = BitUtil.set(status, AUTO_DETECT_WAS_RUN_MASK, wasAutoDetectRun);

    return (byte)(status & (AUTO_DETECTED_AS_TEXT_MASK | AUTO_DETECTED_AS_BINARY_MASK | AUTO_DETECT_WAS_RUN_MASK));
  }

  // store auto-detection flags to the persistent FS file attributes
  // writes AUTO_DETECTED_AS_TEXT_MASK, AUTO_DETECTED_AS_BINARY_MASK bits only
  // protected for Upsource
  protected void writeFlagsToCache(@NotNull VirtualFile file, int flags) {
    try (DataOutputStream stream = autoDetectedAttribute.writeAttribute(file)) {
      stream.writeByte(flags & (AUTO_DETECTED_AS_TEXT_MASK | AUTO_DETECTED_AS_BINARY_MASK));
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  void clearCaches() {
    packedFlags.clear();
    if (toLog()) {
      log("F: clearCaches()");
    }
  }

  private void clearPersistentAttributes() {
    int count = fileTypeChangedCount.incrementAndGet();
    autoDetectedAttribute = autoDetectedAttribute.newVersion(count);
    PropertiesComponent.getInstance().setValue("fileTypeChangedCounter", Integer.toString(count));
    if (toLog()) {
      log("F: clearPersistentAttributes()");
    }
  }

  @Nullable //null means the file was not auto-detected as text/binary
  private static FileType textOrBinaryFromCachedFlags(long flags) {
    return BitUtil.isSet(flags, AUTO_DETECTED_AS_TEXT_MASK) ? PlainTextFileType.INSTANCE :
           BitUtil.isSet(flags, AUTO_DETECTED_AS_BINARY_MASK) ? UnknownFileType.INSTANCE :
           null;
  }

  @NotNull
  @Override
  @Deprecated
  public FileType detectFileTypeFromContent(@NotNull VirtualFile file) {
    return file.getFileType();
  }

  private void cacheAutoDetectedFileType(@NotNull VirtualFile file, @NotNull FileType fileType) {
    boolean wasAutodetectedAsText = fileType == PlainTextFileType.INSTANCE;
    boolean wasAutodetectedAsBinary = fileType == UnknownFileType.INSTANCE;

    int flags = BitUtil.set(0, AUTO_DETECTED_AS_TEXT_MASK, wasAutodetectedAsText);
    flags = BitUtil.set(flags, AUTO_DETECTED_AS_BINARY_MASK, wasAutodetectedAsBinary);
    writeFlagsToCache(file, flags);
    if (file instanceof VirtualFileWithId) {
      int id = Math.abs(((VirtualFileWithId)file).getId());
      flags = BitUtil.set(flags, AUTO_DETECT_WAS_RUN_MASK, true);
      flags = BitUtil.set(flags, ATTRIBUTES_WERE_LOADED_MASK, true);
      packedFlags.set(id, flags);

      if (wasAutodetectedAsText || wasAutodetectedAsBinary) {
        file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, null);
        if (toLog()) {
          log("F: cacheAutoDetectedFileType("+file.getName()+") " +
              "cached to " + fileType.getName() +
              " flags = "+ readableFlags(flags)+
              "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): "+file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
        }
        return;
      }
    }
    file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, fileType);
    if (toLog()) {
      log("F: cacheAutoDetectedFileType("+file.getName()+") " +
          "cached to " + fileType.getName() +
          " flags = "+ readableFlags(flags)+
          "; getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY): "+file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY));
    }
  }

  @Override
  public FileType findFileTypeByName(@NotNull String fileTypeName) {
    FileType type = getStdFileType(fileTypeName);
    // TODO: Abstract file types are not std one, so need to be restored specially,
    // currently there are 6 of them and restoration does not happen very often so just iteration is enough
    if (type == PlainTextFileType.INSTANCE && !fileTypeName.equals(type.getName())) {
      for (FileType fileType: mySchemeManager.getAllSchemes()) {
        if (fileTypeName.equals(fileType.getName())) {
          return fileType;
        }
      }
    }
    return type;
  }

  private static boolean isDetectable(@NotNull final VirtualFile file) {
    if (file.isDirectory() || !file.isValid() || file.is(VFileProperty.SPECIAL) || file.getLength() == 0) {
      // for empty file there is still hope its type will change
      return false;
    }
    return file.getFileSystem() instanceof FileSystemInterface;
  }

  private int readSafely(InputStream stream, byte[] buffer, int offset, int length) throws IOException {
    int n = stream.read(buffer, offset, length);
    if (n <= 0) {
      // maybe locked because someone else is writing to it
      // repeat inside read action to guarantee all writes are finished
      if (toLog()) {
        log("F: processFirstBytes(): inputStream.read() returned "+n+"; retrying with read action. stream="+ streamInfo(stream));
      }
      n = ApplicationManager.getApplication().runReadAction((ThrowableComputable<Integer, IOException>)() -> stream.read(buffer, offset, length));
      if (toLog()) {
        log("F: processFirstBytes(): under read action inputStream.read() returned "+n+"; stream="+ streamInfo(stream));
      }
    }
    return n;
  }

  @NotNull
  private FileType detectFromContentAndCache(@NotNull final VirtualFile file) throws IOException {
    long start = System.currentTimeMillis();
    FileType fileType = UnknownFileType.INSTANCE;
    InputStream inputStream = ((FileSystemInterface)file.getFileSystem()).getInputStream(file);
    try {
      if (toLog()) {
        log("F: detectFromContentAndCache(" + file.getName() + "):" + " inputStream=" + streamInfo(inputStream));
      }

      int fileLength = (int)file.getLength();

      byte[] buffer = fileLength <= FileUtilRt.THREAD_LOCAL_BUFFER_LENGTH
                      ? FileUtilRt.getThreadLocalBuffer()
                      : new byte[Math.min(fileLength, FileUtilRt.getUserContentLoadLimit())];

      int n = readSafely(inputStream, buffer, 0, buffer.length);
      if (n<=0) return UnknownFileType.INSTANCE;

      fileType = ReadAction.compute(() -> detect(file, buffer, n));
    }
    finally {
      try {
        if (toLog()) {
          try (InputStream newStream = ((FileSystemInterface)file.getFileSystem()).getInputStream(file)) {
            byte[] buffer = new byte[50];
            int n = newStream.read(buffer, 0, buffer.length);
            log("F: detectFromContentAndCache(" + file.getName() + "): result: " + fileType.getName() +
                "; stream: " + streamInfo(inputStream) +
                "; newStream: " + streamInfo(newStream) +
                "; read: " + n +
                "; buffer: " + Arrays.toString(buffer));
          }
        }
      }
      finally {
        inputStream.close();
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug(file + "; type=" + fileType.getDescription() + "; " + counterAutoDetect);
    }

    cacheAutoDetectedFileType(file, fileType);
    counterAutoDetect.incrementAndGet();
    long elapsed = System.currentTimeMillis() - start;
    elapsedAutoDetect.addAndGet(elapsed);

    return fileType;
  }

  @NotNull
  private FileType detect(@NotNull VirtualFile file, @NotNull byte[] bytes, int length) {
    // use PlainTextFileType because it doesn't supply its own charset detector
    // help set charset in the process to avoid double charset detection from content
    return LoadTextUtil.processTextFromBinaryPresentationOrNull(bytes, length,
                                                                file, true, true,
                                                                PlainTextFileType.INSTANCE, (@Nullable CharSequence text) -> {
        FileTypeDetector[] detectors = Extensions.getExtensions(FileTypeDetector.EP_NAME);
        if (toLog()) {
          log("F: detectFromContentAndCache.processFirstBytes(" + file.getName() + "): bytes length=" + length +
              "; isText=" + (text != null) + "; text='" + (text == null ? null : StringUtil.first(text, 100, true)) + "'" +
              ", detectors=" + Arrays.toString(detectors));
        }
        FileType detected = null;
        ByteSequence firstBytes = new ByteArraySequence(bytes, 0, length);
        for (FileTypeDetector detector : detectors) {
          try {
            detected = detector.detect(file, firstBytes, text);
          }
          catch (Exception e) {
            LOG.error("Detector " + detector + " (" + detector.getClass() + ") exception occurred:", e);
          }
          if (detected != null) {
            if (toLog()) {
              log("F: detectFromContentAndCache.processFirstBytes(" + file.getName() + "): detector " + detector + " type as " + detected.getName());
            }
            break;
          }
        }

        if (detected == null) {
          detected = text == null ? UnknownFileType.INSTANCE : PlainTextFileType.INSTANCE;
          if (toLog()) {
            log("F: detectFromContentAndCache.processFirstBytes(" + file.getName() + "): " +
                "no detector was able to detect. assigned " + detected.getName());
          }
        }
        return detected;
      });
  }

  // for diagnostics
  @SuppressWarnings("ConstantConditions")
  private static Object streamInfo(InputStream stream) throws IOException {
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

  @Override
  public boolean isFileOfType(@NotNull VirtualFile file, @NotNull FileType type) {
    if (type instanceof FileTypeIdentifiableByVirtualFile) {
      return ((FileTypeIdentifiableByVirtualFile)type).isMyFileType(file);
    }

    return getFileTypeByFileName(file.getNameSequence()) == type;
  }

  @Override
  @NotNull
  public FileType getFileTypeByExtension(@NotNull String extension) {
    return getFileTypeByFileName("IntelliJ_IDEA_RULES." + extension);
  }

  @Override
  public void registerFileType(@NotNull FileType fileType) {
    //noinspection deprecation
    registerFileType(fileType, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @Override
  public void registerFileType(@NotNull final FileType type, @NotNull final List<FileNameMatcher> defaultAssociations) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      fireBeforeFileTypesChanged();
      registerFileTypeWithoutNotification(type, defaultAssociations, true);
      fireFileTypesChanged();
    });
  }

  @Override
  public void unregisterFileType(@NotNull final FileType fileType) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      fireBeforeFileTypesChanged();
      unregisterFileTypeWithoutNotification(fileType);
      fireFileTypesChanged();
    });
  }

  private void unregisterFileTypeWithoutNotification(@NotNull FileType fileType) {
    myPatternsTable.removeAllAssociations(fileType);
    mySchemeManager.removeScheme(fileType);
    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      final FileTypeIdentifiableByVirtualFile fakeFileType = (FileTypeIdentifiableByVirtualFile)fileType;
      mySpecialFileTypes = ArrayUtil.remove(mySpecialFileTypes, fakeFileType, FileTypeIdentifiableByVirtualFile.ARRAY_FACTORY);
    }
  }

  @Override
  @NotNull
  public FileType[] getRegisteredFileTypes() {
    Collection<FileType> fileTypes = mySchemeManager.getAllSchemes();
    return fileTypes.toArray(new FileType[fileTypes.size()]);
  }

  @Override
  @NotNull
  public String getExtension(@NotNull String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index < 0) return "";
    return fileName.substring(index + 1);
  }

  @Override
  @NotNull
  public String getIgnoredFilesList() {
    Set<String> masks = myIgnoredPatterns.getIgnoreMasks();
    return masks.isEmpty() ? "" : StringUtil.join(masks, ";") + ";";
  }

  @Override
  public void setIgnoredFilesList(@NotNull String list) {
    fireBeforeFileTypesChanged();
    myIgnoredFileCache.clearCache();
    myIgnoredPatterns.setIgnoreMasks(list);
    fireFileTypesChanged();
  }

  @Override
  public boolean isIgnoredFilesListEqualToCurrent(@NotNull String list) {
    Set<String> tempSet = new THashSet<>();
    StringTokenizer tokenizer = new StringTokenizer(list, ";");
    while (tokenizer.hasMoreTokens()) {
      tempSet.add(tokenizer.nextToken());
    }
    return tempSet.equals(myIgnoredPatterns.getIgnoreMasks());
  }

  @Override
  public boolean isFileIgnored(@NotNull String name) {
    return myIgnoredPatterns.isIgnored(name);
  }

  @Override
  public boolean isFileIgnored(@NotNull VirtualFile file) {
    return myIgnoredFileCache.isFileIgnored(file);
  }

  @Override
  @NotNull
  public String[] getAssociatedExtensions(@NotNull FileType type) {
    //noinspection deprecation
    return myPatternsTable.getAssociatedExtensions(type);
  }

  @Override
  @NotNull
  public List<FileNameMatcher> getAssociations(@NotNull FileType type) {
    return myPatternsTable.getAssociations(type);
  }

  @Override
  public void associate(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
    associate(type, matcher, true);
  }

  @Override
  public void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
    removeAssociation(type, matcher, true);
  }

  @Override
  public void fireBeforeFileTypesChanged() {
    FileTypeEvent event = new FileTypeEvent(this);
    myMessageBus.syncPublisher(TOPIC).beforeFileTypesChanged(event);
  }

  private final AtomicInteger fileTypeChangedCount;
  @Override
  public void fireFileTypesChanged() {
    clearCaches();
    clearPersistentAttributes();
    myMessageBus.syncPublisher(TOPIC).fileTypesChanged(new FileTypeEvent(this));
  }

  private final Map<FileTypeListener, MessageBusConnection> myAdapters = new HashMap<>();

  @Override
  public void addFileTypeListener(@NotNull FileTypeListener listener) {
    final MessageBusConnection connection = myMessageBus.connect();
    connection.subscribe(TOPIC, listener);
    myAdapters.put(listener, connection);
  }

  @Override
  public void removeFileTypeListener(@NotNull FileTypeListener listener) {
    final MessageBusConnection connection = myAdapters.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }

  @Override
  public void loadState(Element state) {
    int savedVersion = StringUtilRt.parseInt(state.getAttributeValue(ATTRIBUTE_VERSION), 0);

    for (Element element : state.getChildren()) {
      if (element.getName().equals(ELEMENT_IGNORE_FILES)) {
        myIgnoredPatterns.setIgnoreMasks(element.getAttributeValue(ATTRIBUTE_LIST));
      }
      else if (AbstractFileType.ELEMENT_EXTENSION_MAP.equals(element.getName())) {
        readGlobalMappings(element, false);
      }
    }

    if (savedVersion < 4) {
      if (savedVersion == 0) {
        addIgnore(".svn");
      }

      if (savedVersion < 2) {
        restoreStandardFileExtensions();
      }

      addIgnore("*.pyc");
      addIgnore("*.pyo");
      addIgnore(".git");
    }

    if (savedVersion < 5) {
      addIgnore("*.hprof");
    }

    if (savedVersion < 6) {
      addIgnore("_svn");
    }

    if (savedVersion < 7) {
      addIgnore(".hg");
    }

    if (savedVersion < 8) {
      addIgnore("*~");
    }

    if (savedVersion < 9) {
      addIgnore("__pycache__");
    }

    if (savedVersion < 11) {
      addIgnore("*.rbc");
    }

    if (savedVersion < 13) {
      // we want *.lib back since it's an important user artifact for CLion, also for IDEA project itself, since we have some libs.
      unignoreMask("*.lib");
    }

    if (savedVersion < 15) {
      // we want .bundle back, bundler keeps useful data there
      unignoreMask(".bundle");
    }

    if (savedVersion < 16) {
      // we want .tox back to allow users selecting interpreters from it
      unignoreMask(".tox");
    }

    if (savedVersion < 17) {
      addIgnore("*.rbc");
    }

    myIgnoredFileCache.clearCache();

    String counter = JDOMExternalizer.readString(state, "fileTypeChangedCounter");
    if (counter != null) {
      fileTypeChangedCount.set(StringUtilRt.parseInt(counter, 0));
      autoDetectedAttribute = autoDetectedAttribute.newVersion(fileTypeChangedCount.get());
    }
  }

  private void unignoreMask(@NotNull final String maskToRemove) {
    final Set<String> masks = new LinkedHashSet<>(myIgnoredPatterns.getIgnoreMasks());
    masks.remove(maskToRemove);

    myIgnoredPatterns.clearPatterns();
    for (final String each : masks) {
      myIgnoredPatterns.addIgnoreMask(each);
    }
  }

  private void readGlobalMappings(@NotNull Element e, boolean isAddToInit) {
    for (Pair<FileNameMatcher, String> association : AbstractFileType.readAssociations(e)) {
      FileType type = getFileTypeByName(association.getSecond());
      FileNameMatcher matcher = association.getFirst();
      if (type != null) {
        if (PlainTextFileType.INSTANCE == type) {
          FileType newFileType = myPatternsTable.findAssociatedFileType(matcher);
          if (newFileType != null && newFileType != PlainTextFileType.INSTANCE && newFileType != UnknownFileType.INSTANCE) {
            myRemovedMappings.put(matcher, Pair.create(newFileType, false));
          }
        }
        associate(type, matcher, false);
        if (isAddToInit) {
          myInitialAssociations.addAssociation(matcher, type);
        }
      }
      else {
        myUnresolvedMappings.put(matcher, association.getSecond());
      }
    }

    List<Trinity<FileNameMatcher, String, Boolean>> removedAssociations = AbstractFileType.readRemovedAssociations(e);
    for (Trinity<FileNameMatcher, String, Boolean> trinity : removedAssociations) {
      FileType type = getFileTypeByName(trinity.getSecond());
      FileNameMatcher matcher = trinity.getFirst();
      if (type != null) {
        removeAssociation(type, matcher, false);
      }
      else {
        myUnresolvedRemovedMappings.put(matcher, Trinity.create(trinity.getSecond(), myUnresolvedMappings.get(matcher), trinity.getThird()));
      }
    }
  }

  private void addIgnore(@NonNls @NotNull String ignoreMask) {
    myIgnoredPatterns.addIgnoreMask(ignoreMask);
  }

  private void restoreStandardFileExtensions() {
    for (final String name : FILE_TYPES_WITH_PREDEFINED_EXTENSIONS) {
      final StandardFileType stdFileType = myStandardFileTypes.get(name);
      if (stdFileType != null) {
        FileType fileType = stdFileType.fileType;
        for (FileNameMatcher matcher : myPatternsTable.getAssociations(fileType)) {
          FileType defaultFileType = myInitialAssociations.findAssociatedFileType(matcher);
          if (defaultFileType != null && defaultFileType != fileType) {
            removeAssociation(fileType, matcher, false);
            associate(defaultFileType, matcher, false);
          }
        }

        for (FileNameMatcher matcher : myInitialAssociations.getAssociations(fileType)) {
          associate(fileType, matcher, false);
        }
      }
    }
  }

  @NotNull
  @Override
  public Element getState() {
    Element state = new Element("state");

    Set<String> masks = myIgnoredPatterns.getIgnoreMasks();
    String ignoreFiles;
    if (masks.isEmpty()) {
      ignoreFiles = "";
    }
    else {
      String[] strings = ArrayUtil.toStringArray(masks);
      Arrays.sort(strings);
      ignoreFiles = StringUtil.join(strings, ";") + ";";
    }

    if (!ignoreFiles.equalsIgnoreCase(DEFAULT_IGNORED)) {
      // empty means empty list - we need to distinguish null and empty to apply or not to apply default value
      state.addContent(new Element(ELEMENT_IGNORE_FILES).setAttribute(ATTRIBUTE_LIST, ignoreFiles));
    }

    Element map = new Element(AbstractFileType.ELEMENT_EXTENSION_MAP);

    List<FileType> notExternalizableFileTypes = new ArrayList<>();
    for (FileType type : mySchemeManager.getAllSchemes()) {
      if (!(type instanceof AbstractFileType) || myDefaultTypes.contains(type)) {
        notExternalizableFileTypes.add(type);
      }
    }
    if (!notExternalizableFileTypes.isEmpty()) {
      Collections.sort(notExternalizableFileTypes, Comparator.comparing(FileType::getName));
      for (FileType type : notExternalizableFileTypes) {
        writeExtensionsMap(map, type, true);
      }
    }

    if (!myUnresolvedMappings.isEmpty()) {
      FileNameMatcher[] unresolvedMappingKeys = myUnresolvedMappings.keySet().toArray(new FileNameMatcher[myUnresolvedMappings.size()]);
      Arrays.sort(unresolvedMappingKeys, Comparator.comparing(FileNameMatcher::getPresentableString));

      for (FileNameMatcher fileNameMatcher : unresolvedMappingKeys) {
        Element content = AbstractFileType.writeMapping(myUnresolvedMappings.get(fileNameMatcher), fileNameMatcher, true);
        if (content != null) {
          map.addContent(content);
        }
      }
    }

    if (!map.getChildren().isEmpty()) {
      state.addContent(map);
    }

    if (!state.getChildren().isEmpty()) {
      state.setAttribute(ATTRIBUTE_VERSION, String.valueOf(VERSION));
    }
    return state;
  }

  private void writeExtensionsMap(@NotNull Element map, @NotNull FileType type, boolean specifyTypeName) {
    List<FileNameMatcher> associations = myPatternsTable.getAssociations(type);
    Set<FileNameMatcher> defaultAssociations = new THashSet<>(myInitialAssociations.getAssociations(type));

    for (FileNameMatcher matcher : associations) {
      if (defaultAssociations.contains(matcher)) {
        defaultAssociations.remove(matcher);
      }
      else if (shouldSave(type)) {
        Element content = AbstractFileType.writeMapping(type.getName(), matcher, specifyTypeName);
        if (content != null) {
          map.addContent(content);
        }
      }
    }

    for (FileNameMatcher matcher : defaultAssociations) {
      Element content = AbstractFileType.writeRemovedMapping(type, matcher, specifyTypeName, isApproved(matcher));
      if (content != null) {
        map.addContent(content);
      }
    }
  }

  private boolean isApproved(@NotNull FileNameMatcher matcher) {
    Pair<FileType, Boolean> pair = myRemovedMappings.get(matcher);
    return pair != null && pair.getSecond();
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  @Nullable
  private FileType getFileTypeByName(@NotNull String name) {
    return mySchemeManager.findSchemeByName(name);
  }

  @NotNull
  private static List<FileNameMatcher> parse(@Nullable String semicolonDelimited) {
    if (semicolonDelimited == null) {
      return Collections.emptyList();
    }

    StringTokenizer tokenizer = new StringTokenizer(semicolonDelimited, FileTypeConsumer.EXTENSION_DELIMITER, false);
    ArrayList<FileNameMatcher> list = new ArrayList<>(semicolonDelimited.length() / "py;".length());
    while (tokenizer.hasMoreTokens()) {
      list.add(new ExtensionFileNameMatcher(tokenizer.nextToken().trim()));
    }
    return list;
  }

  /**
   * Registers a standard file type. Doesn't notifyListeners any change events.
   */
  private void registerFileTypeWithoutNotification(@NotNull FileType fileType, @NotNull List<FileNameMatcher> matchers, boolean addScheme) {
    if (addScheme) {
      mySchemeManager.addScheme(fileType);
    }
    for (FileNameMatcher matcher : matchers) {
      myPatternsTable.addAssociation(matcher, fileType);
      myInitialAssociations.addAssociation(matcher, fileType);
    }

    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      mySpecialFileTypes = ArrayUtil.append(mySpecialFileTypes, (FileTypeIdentifiableByVirtualFile)fileType, FileTypeIdentifiableByVirtualFile.ARRAY_FACTORY);
    }
  }

  private void bindUnresolvedMappings(@NotNull FileType fileType) {
    for (FileNameMatcher matcher : new THashSet<>(myUnresolvedMappings.keySet())) {
      String name = myUnresolvedMappings.get(matcher);
      if (Comparing.equal(name, fileType.getName())) {
        myPatternsTable.addAssociation(matcher, fileType);
        myUnresolvedMappings.remove(matcher);
      }
    }

    for (FileNameMatcher matcher : new THashSet<>(myUnresolvedRemovedMappings.keySet())) {
      Trinity<String, String, Boolean> trinity = myUnresolvedRemovedMappings.get(matcher);
      if (Comparing.equal(trinity.getFirst(), fileType.getName())) {
        removeAssociation(fileType, matcher, false);
        myUnresolvedRemovedMappings.remove(matcher);
      }
    }
  }

  @NotNull
  private FileType loadFileType(@NotNull Element typeElement, boolean isDefault) {
    String fileTypeName = typeElement.getAttributeValue(ATTRIBUTE_NAME);
    String fileTypeDescr = typeElement.getAttributeValue(ATTRIBUTE_DESCRIPTION);
    String iconPath = typeElement.getAttributeValue("icon");

    String extensionsStr = StringUtil.nullize(typeElement.getAttributeValue("extensions"));
    if (isDefault && extensionsStr != null) {
      // todo support wildcards
      extensionsStr = filterAlreadyRegisteredExtensions(extensionsStr);
    }

    FileType type = isDefault ? getFileTypeByName(fileTypeName) : null;
    if (type != null) {
      return type;
    }

    Element element = typeElement.getChild(AbstractFileType.ELEMENT_HIGHLIGHTING);
    if (element == null) {
      for (CustomFileTypeFactory factory : CustomFileTypeFactory.EP_NAME.getExtensions()) {
        type = factory.createFileType(typeElement);
        if (type != null) {
          break;
        }
      }

      if (type == null) {
        type = new UserBinaryFileType();
      }
    }
    else {
      SyntaxTable table = AbstractFileType.readSyntaxTable(element);
      type = new AbstractFileType(table);
      ((AbstractFileType)type).initSupport();
    }

    setFileTypeAttributes((UserFileType)type, fileTypeName, fileTypeDescr, iconPath);
    registerFileTypeWithoutNotification(type, parse(extensionsStr), isDefault);

    if (isDefault) {
      myDefaultTypes.add(type);
      if (type instanceof ExternalizableFileType) {
        ((ExternalizableFileType)type).markDefaultSettings();
      }
    }
    else {
      Element extensions = typeElement.getChild(AbstractFileType.ELEMENT_EXTENSION_MAP);
      if (extensions != null) {
        for (Pair<FileNameMatcher, String> association : AbstractFileType.readAssociations(extensions)) {
          associate(type, association.getFirst(), false);
        }

        for (Trinity<FileNameMatcher, String, Boolean> removedAssociation : AbstractFileType.readRemovedAssociations(extensions)) {
          removeAssociation(type, removedAssociation.getFirst(), false);
        }
      }
    }
    return type;
  }

  @Nullable
  private String filterAlreadyRegisteredExtensions(@NotNull String semicolonDelimited) {
    StringTokenizer tokenizer = new StringTokenizer(semicolonDelimited, FileTypeConsumer.EXTENSION_DELIMITER, false);
    StringBuilder builder = null;
    while (tokenizer.hasMoreTokens()) {
      String extension = tokenizer.nextToken().trim();
      if (getFileTypeByExtension(extension) == UnknownFileType.INSTANCE) {
        if (builder == null) {
          builder = new StringBuilder();
        }
        else if (builder.length() > 0) {
          builder.append(FileTypeConsumer.EXTENSION_DELIMITER);
        }
        builder.append(extension);
      }
    }
    return builder == null ? null : builder.toString();
  }

  private static void setFileTypeAttributes(@NotNull UserFileType fileType, @Nullable String name, @Nullable String description, @Nullable String iconPath) {
    if (!StringUtil.isEmptyOrSpaces(iconPath)) {
      fileType.setIcon(IconLoader.getIcon(iconPath));
    }
    if (description != null) {
      fileType.setDescription(description);
    }
    if (name != null) {
      fileType.setName(name);
    }
  }

  private static boolean shouldSave(@NotNull FileType fileType) {
    return fileType != UnknownFileType.INSTANCE && !fileType.isReadOnly();
  }

  // -------------------------------------------------------------------------
  // Setup
  // -------------------------------------------------------------------------


  @NotNull
  FileTypeAssocTable getExtensionMap() {
    return myPatternsTable;
  }

  void setPatternsTable(@NotNull Set<FileType> fileTypes, @NotNull FileTypeAssocTable<FileType> assocTable) {
    fireBeforeFileTypesChanged();
    for (FileType existing : getRegisteredFileTypes()) {
      if (!fileTypes.contains(existing)) {
        mySchemeManager.removeScheme(existing);
      }
    }
    for (FileType fileType : fileTypes) {
      mySchemeManager.addScheme(fileType);
      if (fileType instanceof AbstractFileType) {
        ((AbstractFileType)fileType).initSupport();
      }
    }
    myPatternsTable = assocTable.copy();
    fireFileTypesChanged();
  }

  public void associate(@NotNull FileType fileType, @NotNull FileNameMatcher matcher, boolean fireChange) {
    if (!myPatternsTable.isAssociatedWith(fileType, matcher)) {
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myPatternsTable.addAssociation(matcher, fileType);
      if (fireChange) {
        fireFileTypesChanged();
      }
    }
  }

  public void removeAssociation(@NotNull FileType fileType, @NotNull FileNameMatcher matcher, boolean fireChange) {
    if (myPatternsTable.isAssociatedWith(fileType, matcher)) {
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myPatternsTable.removeAssociation(matcher, fileType);
      if (fireChange) {
        fireFileTypesChanged();
      }
    }
  }

  @Override
  @Nullable
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file) {
    FileType type = file.getFileType();
    if (type == UnknownFileType.INSTANCE) {
      type = FileTypeChooser.associateFileType(file.getName());
    }

    return type;
  }

  @Override
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file, @NotNull Project project) {
    return FileTypeChooser.getKnownFileTypeOrAssociate(file, project);
  }

  private void registerReDetectedMappings(@NotNull StandardFileType pair) {
    FileType fileType = pair.fileType;
    if (fileType == PlainTextFileType.INSTANCE) return;
    for (FileNameMatcher matcher : pair.matchers) {
      registerReDetectedMapping(fileType, matcher);
      if (matcher instanceof ExtensionFileNameMatcher) {
        // also check exact file name matcher
        ExtensionFileNameMatcher extMatcher = (ExtensionFileNameMatcher)matcher;
        registerReDetectedMapping(fileType, new ExactFileNameMatcher("." + extMatcher.getExtension()));
      }
    }
  }

  private void registerReDetectedMapping(@NotNull FileType fileType, @NotNull FileNameMatcher matcher) {
    String typeName = myUnresolvedMappings.get(matcher);
    if (typeName != null && !typeName.equals(fileType.getName())) {
      Trinity<String, String, Boolean> trinity = myUnresolvedRemovedMappings.get(matcher);
      myRemovedMappings.put(matcher, Pair.create(fileType, trinity != null && trinity.third));
      myUnresolvedMappings.remove(matcher);
    }
  }

  @NotNull
  Map<FileNameMatcher, Pair<FileType, Boolean>> getRemovedMappings() {
    return myRemovedMappings;
  }

  @TestOnly
  void clearForTests() {
    for (StandardFileType fileType : myStandardFileTypes.values()) {
      myPatternsTable.removeAllAssociations(fileType.fileType);
    }
    myStandardFileTypes.clear();
    myUnresolvedMappings.clear();
    mySchemeManager.setSchemes(Collections.emptyList());
  }

  @Override
  public void dispose() {
    LOG.info("FileTypeManager: "+ counterAutoDetect +" auto-detected files\nElapsed time on auto-detect: "+elapsedAutoDetect+" ms");
  }
}
