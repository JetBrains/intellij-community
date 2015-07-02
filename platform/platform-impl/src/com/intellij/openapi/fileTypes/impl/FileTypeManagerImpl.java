/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.TransferToPooledThreadQueue;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.*;
import com.intellij.openapi.options.BaseSchemeProcessor;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SchemesManagerFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.FileSystemInterface;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.ConcurrentPackedBitsArray;
import com.intellij.util.containers.ContainerUtil;
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@State(
  name = "FileTypeManager",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/filetypes.xml"),
  additionalExportFile = FileTypeManagerImpl.FILE_SPEC
)
public class FileTypeManagerImpl extends FileTypeManagerEx implements PersistentStateComponent<Element>, ApplicationComponent, Disposable {
  private static final Logger LOG = Logger.getInstance(FileTypeManagerImpl.class);

  // You must update all existing default configurations accordingly
  private static final int VERSION = 15;
  private static final Key<FileType> FILE_TYPE_KEY = Key.create("FILE_TYPE_KEY");
  // cached auto-detected file type. If the file was auto-detected as plain text or binary
  // then the value is null and autoDetectedAsText, autoDetectedAsBinary and autoDetectWasRun sets are used instead.
  private static final Key<FileType> DETECTED_FROM_CONTENT_FILE_TYPE_KEY = Key.create("DETECTED_FROM_CONTENT_FILE_TYPE_KEY");
  private static final int DETECT_BUFFER_SIZE = 8192; // the number of bytes to read from the file to feed to the file type detector

  @NonNls
  private static final String DEFAULT_IGNORED =
    "*.hprof;*.pyc;*.pyo;*.rbc;*~;.DS_Store;.git;.hg;.svn;CVS;RCS;SCCS;__pycache__;.tox;_svn;rcs;vssver.scc;vssver2.scc;";

  private static boolean RE_DETECT_ASYNC = !ApplicationManager.getApplication().isUnitTestMode();
  private final Set<FileType> myDefaultTypes = new THashSet<FileType>();
  private final List<FileTypeIdentifiableByVirtualFile> mySpecialFileTypes = new ArrayList<FileTypeIdentifiableByVirtualFile>();

  private FileTypeAssocTable<FileType> myPatternsTable = new FileTypeAssocTable<FileType>();
  private final IgnoredPatternSet myIgnoredPatterns = new IgnoredPatternSet();
  private final IgnoredFileCache myIgnoredFileCache = new IgnoredFileCache(myIgnoredPatterns);

  private final FileTypeAssocTable<FileType> myInitialAssociations = new FileTypeAssocTable<FileType>();
  private final Map<FileNameMatcher, String> myUnresolvedMappings = new THashMap<FileNameMatcher, String>();
  private final Map<FileNameMatcher, Trinity<String, String, Boolean>> myUnresolvedRemovedMappings = new THashMap<FileNameMatcher, Trinity<String, String, Boolean>>();
  /** This will contain removed mappings with "approved" states */
  private final Map<FileNameMatcher, Pair<FileType, Boolean>> myRemovedMappings = new THashMap<FileNameMatcher, Pair<FileType, Boolean>>();

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
  private final Map<String, StandardFileType> myStandardFileTypes = new LinkedHashMap<String, StandardFileType>();
  @NonNls
  private static final String[] FILE_TYPES_WITH_PREDEFINED_EXTENSIONS = {"JSP", "JSPX", "DTD", "HTML", "Properties", "XHTML"};
  private final SchemesManager<FileType, AbstractFileType> mySchemesManager;
  @NonNls
  static final String FILE_SPEC = StoragePathMacros.ROOT_CONFIG + "/filetypes";

  // these flags are stored in 'packedFlags' as chunks of four bits
  private static final int AUTO_DETECTED_AS_TEXT_MASK = 1;     // set if the file was auto-detected as text
  private static final int AUTO_DETECTED_AS_BINARY_MASK = 2;   // set if the file was auto-detected as binary
  private static final int AUTO_DETECT_WAS_RUN_MASK = 4;       // set if auto-detection was performed for this file
  private static final int ATTRIBUTES_WERE_LOADED_MASK = 8;    // set if AUTO_* bits above were loaded from the file persistent attributes
  private final ConcurrentPackedBitsArray packedFlags = new ConcurrentPackedBitsArray(4);

  private final AtomicInteger counterAutoDetect = new AtomicInteger();
  private final AtomicLong elapsedAutoDetect = new AtomicLong();

  public FileTypeManagerImpl(MessageBus bus, SchemesManagerFactory schemesManagerFactory, PropertiesComponent propertiesComponent) {
    int fileTypeChangedCounter = StringUtilRt.parseInt(propertiesComponent.getValue("fileTypeChangedCounter"), 0);
    fileTypeChangedCount = new AtomicInteger(fileTypeChangedCounter);
    autoDetectedAttribute = new FileAttribute("AUTO_DETECTION_CACHE_ATTRIBUTE", fileTypeChangedCounter, true);

    myMessageBus = bus;
    mySchemesManager = schemesManagerFactory.createSchemesManager(FILE_SPEC, new BaseSchemeProcessor<AbstractFileType>() {
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
      public State getState(@NotNull AbstractFileType fileType) {
        if (!shouldSave(fileType)) {
          return State.NON_PERSISTENT;
        }
        if (!myDefaultTypes.contains(fileType)) {
          return State.POSSIBLY_CHANGED;
        }
        return fileType.isModified() ? State.POSSIBLY_CHANGED : State.NON_PERSISTENT;
      }

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
        fireBeforeFileTypesChanged();
        myPatternsTable.removeAllAssociations(scheme);
        fireFileTypesChanged();
      }
    }, RoamingType.PER_USER);
    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        Collection<VirtualFile> files = ContainerUtil.map2Set(events, new Function<VFileEvent, VirtualFile>() {
          @Override
          public VirtualFile fun(VFileEvent event) {
            VirtualFile file = event instanceof VFileCreateEvent ? /* avoid expensive find child here */ null : event.getFile();
            return file != null && wasAutoDetectedBefore(file) && isDetectable(file) ? file : null;
          }
        });
        files.remove(null);
        if (toLog()) {
          log("F: VFS events: " + events);
        }
        if (!files.isEmpty() && RE_DETECT_ASYNC) {
          if (toLog()) {
            log("F: queued to redetect: " + files);
          }
          reDetectQueue.offerIfAbsent(files);
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
        register(fileType, new ArrayList<FileNameMatcher>(Arrays.asList(matchers)));
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
        Element defaultFileTypesElement = JDOMUtil.load(URLUtil.openStream(defaultFileTypesUrl));
        for (Element e : defaultFileTypesElement.getChildren()) {
          //noinspection SpellCheckingInspection
          if ("filetypes".equals(e.getName())) {
            for (Element element : e.getChildren(ELEMENT_FILETYPE)) {
              loadFileType(element, true);
            }
          }
          else if (AbstractFileType.ELEMENT_EXTENSION_MAP.equals(e.getName())) {
            readGlobalMappings(e);
          }
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

  private static void log(@SuppressWarnings("UnusedParameters") String message) {
    System.out.println(message);
  }

  private final TransferToPooledThreadQueue<Collection<VirtualFile>> reDetectQueue = new TransferToPooledThreadQueue<Collection<VirtualFile>>("File type re-detect", Conditions.alwaysFalse(), -1, new Processor<Collection<VirtualFile>>() {
    @Override
    public boolean process(Collection<VirtualFile> files) {
      reDetect(files);
      return true;
    }
  });

  @TestOnly
  public void drainReDetectQueue() {
    reDetectQueue.waitFor();
  }

  @TestOnly
  @NotNull
  Collection<VirtualFile> dumpReDetectQueue() {
    return ContainerUtil.flatten(reDetectQueue.dump());
  }

  @TestOnly
  static void reDetectAsync(boolean enable) {
    RE_DETECT_ASYNC = enable;
  }

  private void reDetect(@NotNull Collection<VirtualFile> files) {
    final Collection<VirtualFile> changed = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      boolean shouldRedetect = wasAutoDetectedBefore(file) && isDetectable(file);
      if (toLog()) {
        log("F: Redetect file: " + file.getName() + "; shouldRedetect: " + shouldRedetect);
      }
      if (shouldRedetect) {
        int id = file instanceof VirtualFileWithId ? ((VirtualFileWithId)file).getId() : -1;
        FileType before = getAutoDetectedType(file, id);

        packedFlags.set(id, ATTRIBUTES_WERE_LOADED_MASK);

        file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, null);
        FileType after = getFileTypeByFile(file); // may be back to standard file type
        if (toLog()) {
          log("F: After redetect file: " + file.getName() + "; before: " + before.getName() + "; after: " + after.getName()+"; now getFileType()="+file.getFileType().getName());
        }

        if (before != after) {
          changed.add(file);
          LOG.debug(file+" type was re-detected. Was: "+before.getName()+"; now: "+after.getName());
        }
      }
    }
    if (!changed.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          FileContentUtilCore.reparseFiles(changed);
        }
      }, ApplicationManager.getApplication().getDisposed());
    }
  }

  private boolean wasAutoDetectedBefore(@NotNull VirtualFile file) {
    if (file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY) != null) return true;
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
  public void disposeComponent() {
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
    for (AbstractFileType fileType : mySchemesManager.loadSchemes()) {
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

  @NotNull
  private FileType getFileTypeByFileName(@NotNull CharSequence fileName) {
    FileType type = myPatternsTable.findAssociatedFileType(fileName);
    return type == null ? UnknownFileType.INSTANCE : type;
  }

  public void cacheFileType(@NotNull VirtualFile file, @Nullable FileType fileType) {
    file.putUserData(FILE_TYPE_KEY, fileType);
    if (toLog()) {
      log("F: Cached file type for "+file.getName()+" to "+(fileType == null ? null : fileType.getName()));
    }
  }

  @Override
  @NotNull
  public FileType getFileTypeByFile(@NotNull VirtualFile file) {
    FileType fileType = file.getUserData(FILE_TYPE_KEY);
    if (fileType != null) return fileType;

    if (file instanceof LightVirtualFile) {
      fileType = ((LightVirtualFile)file).getAssignedFileType();
      if (fileType != null) return fileType;
    }

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < mySpecialFileTypes.size(); i++) {
      FileTypeIdentifiableByVirtualFile type = mySpecialFileTypes.get(i);
      if (type.isMyFileType(file)) {
        if (toLog()) {
          log("F: Special file type for "+file.getName()+"; type: "+type.getName());
        }
        return type;
      }
    }

    fileType = getFileTypeByFileName(file.getNameSequence());
    if (fileType != UnknownFileType.INSTANCE) {
      if (toLog()) {
        log("F: By name file type for "+file.getName()+"; type: "+fileType.getName());
      }
      return fileType;
    }

    if (!(file instanceof StubVirtualFile)) {
      fileType = getOrDetectFromContent(file);
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
      boolean autoDetectWasRun = (flags & AUTO_DETECT_WAS_RUN_MASK) != 0;
      if (autoDetectWasRun) {
        FileType type = getAutoDetectedType(file, id);
        if (toLog()) {
          log("F: autodetected getFileType("+file.getName()+") = "+type.getName());
        }
        return type;
      }
      if ((flags & ATTRIBUTES_WERE_LOADED_MASK) == 0) {
        flags = readFlagsFromCache(file);
        packedFlags.set(id, flags);
      }
      boolean wasDetectedAsText = BitUtil.isSet(flags, AUTO_DETECTED_AS_TEXT_MASK);
      boolean wasDetectedAsBinary = BitUtil.isSet(flags, AUTO_DETECTED_AS_BINARY_MASK);
      boolean wasAutoDetectRun = BitUtil.isSet(flags, AUTO_DETECT_WAS_RUN_MASK);
      if (wasAutoDetectRun && (wasDetectedAsText || wasDetectedAsBinary)) {
        return wasDetectedAsText ? FileTypes.PLAIN_TEXT : UnknownFileType.INSTANCE;
      }
    }
    FileType fileType = file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY);
    // run autodetection
    if (fileType == null) {
      fileType = detectFromContent(file);
    }

    if (toLog()) {
      log("F: getFileType after detect run("+file.getName()+") = "+fileType.getName());
    }

    return fileType;
  }

  private volatile FileAttribute autoDetectedAttribute;
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
    status = (byte)BitUtil.set(status, AUTO_DETECT_WAS_RUN_MASK, wasAutoDetectRun);
    status = (byte)BitUtil.set(status, ATTRIBUTES_WERE_LOADED_MASK, true);

    return status;
  }

  protected void writeFlagsToCache(@NotNull VirtualFile file, int flags) {
    DataOutputStream stream = autoDetectedAttribute.writeAttribute(file);
    try {
      try {
        stream.writeByte(flags);
      }
      finally {
        stream.close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void clearCaches() {
    int count = fileTypeChangedCount.incrementAndGet();
    autoDetectedAttribute = autoDetectedAttribute.newVersion(count);
    PropertiesComponent.getInstance().setValue("fileTypeChangedCounter", Integer.toString(count));
    packedFlags.clear();
  }

  @NotNull
  private FileType getAutoDetectedType(@NotNull VirtualFile file, int id) {
    long flags = packedFlags.get(id);
    return BitUtil.isSet(flags, AUTO_DETECTED_AS_TEXT_MASK) ? FileTypes.PLAIN_TEXT :
           BitUtil.isSet(flags, AUTO_DETECTED_AS_BINARY_MASK) ? UnknownFileType.INSTANCE :
           ObjectUtils.notNull(file.getUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY), FileTypes.PLAIN_TEXT);
  }

  @NotNull
  @Override
  @Deprecated
  public FileType detectFileTypeFromContent(@NotNull VirtualFile file) {
    return file.getFileType();
  }

  private void cacheAutoDetectedFileType(@NotNull VirtualFile file, @NotNull FileType fileType) {
    boolean wasAutodetectedAsText = fileType == FileTypes.PLAIN_TEXT;
    boolean wasAutodetectedAsBinary = fileType == FileTypes.UNKNOWN;

    int flags = BitUtil.set(0, AUTO_DETECTED_AS_TEXT_MASK, wasAutodetectedAsText);
    flags = BitUtil.set(flags, AUTO_DETECTED_AS_BINARY_MASK, wasAutodetectedAsBinary);
    writeFlagsToCache(file, flags);
    if (file instanceof VirtualFileWithId) {
      int id = Math.abs(((VirtualFileWithId)file).getId());
      flags = AUTO_DETECT_WAS_RUN_MASK | ATTRIBUTES_WERE_LOADED_MASK;
      flags = BitUtil.set(flags, AUTO_DETECTED_AS_TEXT_MASK, wasAutodetectedAsText);
      flags = BitUtil.set(flags, AUTO_DETECTED_AS_BINARY_MASK, wasAutodetectedAsBinary);
      packedFlags.set(id, flags);

      if (wasAutodetectedAsText || wasAutodetectedAsBinary) {
        file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, null);
        return;
      }
    }
    file.putUserData(DETECTED_FROM_CONTENT_FILE_TYPE_KEY, fileType);
  }

  @Override
  public FileType findFileTypeByName(String fileTypeName) {
    FileType type = getStdFileType(fileTypeName);
    // TODO: Abstract file types are not std one, so need to be restored specially,
    // currently there are 6 of them and restoration does not happen very often so just iteration is enough
    if (type == PlainTextFileType.INSTANCE && !fileTypeName.equals(type.getName())) {
      for (FileType fileType: mySchemesManager.getAllSchemes()) {
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
    return file.getFileSystem() instanceof FileSystemInterface && !SingleRootFileViewProvider.isTooLargeForContentLoading(file);
  }

  @NotNull
  private FileType detectFromContent(@NotNull final VirtualFile file) {
    long start = System.currentTimeMillis();
    try {
      final InputStream inputStream = ((FileSystemInterface)file.getFileSystem()).getInputStream(file);
      final Ref<FileType> result = new Ref<FileType>(UnknownFileType.INSTANCE);
      try {
        FileUtil.processFirstBytes(inputStream, DETECT_BUFFER_SIZE, new Processor<ByteSequence>() {
          @Override
          public boolean process(ByteSequence byteSequence) {
            boolean isText = guessIfText(file, byteSequence);
            CharSequence text;
            if (isText) {
              byte[] bytes = Arrays.copyOf(byteSequence.getBytes(), byteSequence.getLength());
              text = LoadTextUtil.getTextByBinaryPresentation(bytes, file, true, true, UnknownFileType.INSTANCE);
            }
            else {
              text = null;
            }

            FileType detected = null;
            for (FileTypeDetector detector : Extensions.getExtensions(FileTypeDetector.EP_NAME)) {
              try {
                detected = detector.detect(file, byteSequence, text);
              }
              catch (Exception e) {
                LOG.error("Detector " + detector + " (" + detector.getClass() + ") exception occurred:", e);
              }
              if (detected != null) break;
            }

            if (detected == null) {
              detected = isText ? PlainTextFileType.INSTANCE : UnknownFileType.INSTANCE;
            }
            result.set(detected);
            return true;
          }
        });
      }
      finally {
        inputStream.close();
      }
      FileType fileType = result.get();
      if (toLog()) {
        log("F: Redetect run for file: " + file.getName() + "; result: "+fileType.getName());
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
    catch (IOException ignored) {
      return UnknownFileType.INSTANCE; // return unknown, do not cache
    }
  }

  private static boolean guessIfText(@NotNull VirtualFile file, @NotNull ByteSequence byteSequence) {
    byte[] bytes = byteSequence.getBytes();
    Trinity<Charset, CharsetToolkit.GuessedEncoding, byte[]> guessed = LoadTextUtil.guessFromContent(file, bytes, byteSequence.getLength());
    if (guessed == null) return false;
    file.setBOM(guessed.third);
    if (guessed.first != null) {
      // charset was detected unambiguously
      return true;
    }
    // use wild guess
    CharsetToolkit.GuessedEncoding guess = guessed.second;
    return guess != null && (guess == CharsetToolkit.GuessedEncoding.VALID_UTF8 || guess == CharsetToolkit.GuessedEncoding.SEVEN_BIT);
  }

  @Override
  public boolean isFileOfType(@NotNull VirtualFile file, @NotNull FileType type) {
    if (type instanceof FileTypeIdentifiableByVirtualFile) {
      return ((FileTypeIdentifiableByVirtualFile)type).isMyFileType(file);
    }

    return getFileTypeByFileName(file.getName()) == type;
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
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        fireBeforeFileTypesChanged();
        registerFileTypeWithoutNotification(type, defaultAssociations, true);
        fireFileTypesChanged();
      }
    });
  }

  @Override
  public void unregisterFileType(@NotNull final FileType fileType) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        fireBeforeFileTypesChanged();
        unregisterFileTypeWithoutNotification(fileType);
        fireFileTypesChanged();
      }
    });
  }

  private void unregisterFileTypeWithoutNotification(FileType fileType) {
    myPatternsTable.removeAllAssociations(fileType);
    mySchemesManager.removeScheme(fileType);
    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      final FileTypeIdentifiableByVirtualFile fakeFileType = (FileTypeIdentifiableByVirtualFile)fileType;
      mySpecialFileTypes.remove(fakeFileType);
    }
  }

  @Override
  @NotNull
  public FileType[] getRegisteredFileTypes() {
    Collection<FileType> fileTypes = mySchemesManager.getAllSchemes();
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
    Set<String> tempSet = new THashSet<String>();
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
  public boolean isFileIgnored(@NonNls @NotNull VirtualFile file) {
    return myIgnoredFileCache.isFileIgnored(file);
  }

  @Override
  @SuppressWarnings({"deprecation"})
  @NotNull
  public String[] getAssociatedExtensions(@NotNull FileType type) {
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
    myMessageBus.syncPublisher(TOPIC).fileTypesChanged(new FileTypeEvent(this));
  }

  private final Map<FileTypeListener, MessageBusConnection> myAdapters = new HashMap<FileTypeListener, MessageBusConnection>();

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
        readGlobalMappings(element);
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

    if (savedVersion < 14) {
      addIgnore(".tox");
    }

    if (savedVersion < 15) {
      // we want .bundle back, bundler keeps useful data there
      unignoreMask(".bundle");
    }
    myIgnoredFileCache.clearCache();

    String counter = JDOMExternalizer.readString(state, "fileTypeChangedCounter");
    if (counter != null) {
      fileTypeChangedCount.set(StringUtilRt.parseInt(counter, 0));
      autoDetectedAttribute = autoDetectedAttribute.newVersion(fileTypeChangedCount.get());
    }
  }

  private void unignoreMask(@NotNull final String maskToRemove) {
    final Set<String> masks = new LinkedHashSet<String>(myIgnoredPatterns.getIgnoreMasks());
    masks.remove(maskToRemove);

    myIgnoredPatterns.clearPatterns();
    for (final String each : masks) {
      myIgnoredPatterns.addIgnoreMask(each);
    }
  }

  private void readGlobalMappings(@NotNull Element e) {
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

  @Nullable
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

    List<FileType> notExternalizableFileTypes = new ArrayList<FileType>();
    for (FileType type : mySchemesManager.getAllSchemes()) {
      if (!(type instanceof AbstractFileType) || myDefaultTypes.contains(type)) {
        notExternalizableFileTypes.add(type);
      }
    }
    if (!notExternalizableFileTypes.isEmpty()) {
      Collections.sort(notExternalizableFileTypes, new Comparator<FileType>() {
        @Override
        public int compare(@NotNull FileType o1, @NotNull FileType o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });
      for (FileType type : notExternalizableFileTypes) {
        writeExtensionsMap(map, type, true);
      }
    }

    if (!myUnresolvedMappings.isEmpty()) {
      FileNameMatcher[] unresolvedMappingKeys = myUnresolvedMappings.keySet().toArray(new FileNameMatcher[myUnresolvedMappings.size()]);
      Arrays.sort(unresolvedMappingKeys, new Comparator<FileNameMatcher>() {
        @Override
        public int compare(FileNameMatcher o1, FileNameMatcher o2) {
          return o1.getPresentableString().compareTo(o2.getPresentableString());
        }
      });

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
    Set<FileNameMatcher> defaultAssociations = new THashSet<FileNameMatcher>(myInitialAssociations.getAssociations(type));

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

  private boolean isApproved(FileNameMatcher matcher) {
    Pair<FileType, Boolean> pair = myRemovedMappings.get(matcher);
    return pair != null && pair.getSecond();
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  @Nullable
  private FileType getFileTypeByName(@NotNull String name) {
    return mySchemesManager.findSchemeByName(name);
  }

  @NotNull
  private static List<FileNameMatcher> parse(@Nullable String semicolonDelimited) {
    if (semicolonDelimited == null) {
      return Collections.emptyList();
    }

    StringTokenizer tokenizer = new StringTokenizer(semicolonDelimited, FileTypeConsumer.EXTENSION_DELIMITER, false);
    ArrayList<FileNameMatcher> list = new ArrayList<FileNameMatcher>();
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
      mySchemesManager.addScheme(fileType);
    }
    for (FileNameMatcher matcher : matchers) {
      myPatternsTable.addAssociation(matcher, fileType);
      myInitialAssociations.addAssociation(matcher, fileType);
    }

    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      mySpecialFileTypes.add((FileTypeIdentifiableByVirtualFile)fileType);
    }
  }

  private void bindUnresolvedMappings(@NotNull FileType fileType) {
    for (FileNameMatcher matcher : new THashSet<FileNameMatcher>(myUnresolvedMappings.keySet())) {
      String name = myUnresolvedMappings.get(matcher);
      if (Comparing.equal(name, fileType.getName())) {
        myPatternsTable.addAssociation(matcher, fileType);
        myUnresolvedMappings.remove(matcher);
      }
    }

    for (FileNameMatcher matcher : new THashSet<FileNameMatcher>(myUnresolvedRemovedMappings.keySet())) {
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

  private static boolean shouldSave(FileType fileType) {
    return fileType != FileTypes.UNKNOWN && !fileType.isReadOnly();
  }

  // -------------------------------------------------------------------------
  // Setup
  // -------------------------------------------------------------------------

  @Override
  @NotNull
  public String getComponentName() {
    return getFileTypeComponentName();
  }

  public static String getFileTypeComponentName() {
    return PlatformUtils.isIdeaCommunity() ? "CommunityFileTypes" : "FileTypeManager";
  }

  @NotNull
  FileTypeAssocTable getExtensionMap() {
    return myPatternsTable;
  }

  void setPatternsTable(@NotNull Set<FileType> fileTypes, @NotNull FileTypeAssocTable<FileType> assocTable) {
    fireBeforeFileTypesChanged();
    for (FileType existing : getRegisteredFileTypes()) {
      if (!fileTypes.contains(existing)) {
        mySchemesManager.removeScheme(existing);
      }
    }
    for (FileType fileType : fileTypes) {
      mySchemesManager.addScheme(fileType);
      if (fileType instanceof AbstractFileType) {
        ((AbstractFileType)fileType).initSupport();
      }
    }
    myPatternsTable = assocTable.copy();
    fireFileTypesChanged();
  }

  public void associate(FileType fileType, FileNameMatcher matcher, boolean fireChange) {
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

  public void removeAssociation(FileType fileType, FileNameMatcher matcher, boolean fireChange) {
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
    if (type != UnknownFileType.INSTANCE) return type;
    return FileTypeChooser.getKnownFileTypeOrAssociate(file.getName());
  }

  @Override
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file, @NotNull Project project) {
    return FileTypeChooser.getKnownFileTypeOrAssociate(file, project);
  }

  private void registerReDetectedMappings(StandardFileType pair) {
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

  Map<FileNameMatcher, Pair<FileType, Boolean>> getRemovedMappings() {
    return myRemovedMappings;
  }

  @TestOnly
  void clearForTests() {
    myStandardFileTypes.clear();
    myUnresolvedMappings.clear();
    mySchemesManager.clearAllSchemes();
  }

  @Override
  public void dispose() {
    LOG.info("FileTypeManager: "+ counterAutoDetect +" auto-detected files\nElapsed time on auto-detect: "+elapsedAutoDetect+" ms");
  }
}
