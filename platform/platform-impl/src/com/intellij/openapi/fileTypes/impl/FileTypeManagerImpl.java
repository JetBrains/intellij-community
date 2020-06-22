// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.StartupAbortedException;
import com.intellij.lang.Language;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.ExternalizableFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.options.NonLazySchemeProcessor;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.options.SchemeState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.*;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBusConnection;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@State(name = "FileTypeManager", storages = @Storage("filetypes.xml"), additionalExportFile = FileTypeManagerImpl.FILE_SPEC )
public class FileTypeManagerImpl extends FileTypeManagerEx implements PersistentStateComponent<Element> {
  static final ExtensionPointName<FileTypeBean> EP_NAME = ExtensionPointName.create("com.intellij.fileType");
  private static final Logger LOG = Logger.getInstance(FileTypeManagerImpl.class);

  // You must update all existing default configurations accordingly
  private static final int VERSION = 17;
  private static final ThreadLocal<Pair<VirtualFile, FileType>> FILE_TYPE_FIXED_TEMPORARILY = new ThreadLocal<>();

  // must be sorted
  @SuppressWarnings("SpellCheckingInspection")
  static final String DEFAULT_IGNORED = "*.hprof;*.pyc;*.pyo;*.rbc;*.yarb;*~;.DS_Store;.git;.hg;.svn;CVS;__pycache__;_svn;vssver.scc;vssver2.scc;";

  private final Set<FileType> myDefaultTypes = new ObjectOpenHashSet<>();
  private final FileTypeDetectionService myDetectionService;
  private FileTypeIdentifiableByVirtualFile[] mySpecialFileTypes = FileTypeIdentifiableByVirtualFile.EMPTY_ARRAY;

  FileTypeAssocTable<FileType> myPatternsTable = new FileTypeAssocTable<>();
  private final IgnoredPatternSet myIgnoredPatterns = new IgnoredPatternSet();
  private final IgnoredFileCache myIgnoredFileCache = new IgnoredFileCache(myIgnoredPatterns);

  private final FileTypeAssocTable<FileType> myInitialAssociations = new FileTypeAssocTable<>();
  private final Map<FileNameMatcher, String> myUnresolvedMappings = CollectionFactory.createMap();
  private final RemovedMappingTracker myRemovedMappingTracker = new RemovedMappingTracker();

  private final Map<String, FileTypeBean> myPendingFileTypes = new LinkedHashMap<>();
  private final FileTypeAssocTable<FileTypeBean> myPendingAssociations = new FileTypeAssocTable<>();
  private final ReadWriteLock myPendingInitializationLock = new ReentrantReadWriteLock();

  @NonNls private static final String ELEMENT_FILETYPE = "filetype";
  @NonNls private static final String ELEMENT_IGNORE_FILES = "ignoreFiles";
  @NonNls private static final String ATTRIBUTE_LIST = "list";

  @NonNls private static final String ATTRIBUTE_VERSION = "version";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_DESCRIPTION = "description";

  private static final class StandardFileType {
    @NotNull private final FileType fileType;
    @NotNull private final List<FileNameMatcher> matchers;

    private StandardFileType(@NotNull FileType fileType, @NotNull List<FileNameMatcher> matchers) {
      this.fileType = fileType;
      this.matchers = matchers;
    }
  }

  private final Map<String, StandardFileType> myStandardFileTypes = new LinkedHashMap<>();
  @NonNls
  private static final String[] FILE_TYPES_WITH_PREDEFINED_EXTENSIONS = {"JSP", "JSPX", "DTD", "HTML", "Properties", "XHTML"};
  private final SchemeManager<FileType> mySchemeManager;
  @NonNls
  static final String FILE_SPEC = "filetypes";

  public FileTypeManagerImpl() {
    mySchemeManager = SchemeManagerFactory.getInstance().create(FILE_SPEC, new NonLazySchemeProcessor<FileType, AbstractFileType>() {
      @NotNull
      @Override
      public AbstractFileType readScheme(@NotNull Element element, boolean duringLoad) {
        if (!duringLoad) {
          fireBeforeFileTypesChanged();
        }
        AbstractFileType type = (AbstractFileType)loadFileType(element, false);
        if (!duringLoad) {
          fireFileTypesChanged(type, null);
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
          app.runWriteAction(() -> fireFileTypesChanged(null, scheme));
        }, ModalityState.NON_MODAL);
      }
    });

    // this should be done BEFORE reading state
    initStandardFileTypes();

    myDetectionService = new FileTypeDetectionService(this);

    myIgnoredPatterns.setIgnoreMasks(DEFAULT_IGNORED);

    EP_NAME.addExtensionPointListener(new ExtensionPointListener<FileTypeBean>() {
      @Override
      public void extensionAdded(@NotNull FileTypeBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        fireBeforeFileTypesChanged();
        initializeMatchers(extension);
        FileType fileType = mergeOrInstantiateFileTypeBean(extension);

        fileTypeChanged(fileType, ApplicationManager.getApplication().isUnitTestMode());
      }

      @Override
      public void extensionRemoved(@NotNull FileTypeBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        if (extension.implementationClass != null) {
          final FileType fileType = findFileTypeByName(extension.name);
          if (fileType == null) return;
          unregisterFileType(fileType);
        }
        else {
          StandardFileType stdFileType = myStandardFileTypes.get(extension.name);
          if (stdFileType != null) {
            unregisterMatchers(stdFileType, extension);
          }
        }
      }
    }, ApplicationManager.getApplication());

  }


  private void unregisterMatchers(@NotNull StandardFileType stdFileType, @NotNull FileTypeBean extension) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      stdFileType.matchers.removeAll(extension.getMatchers());
      for (FileNameMatcher matcher : extension.getMatchers()) {
        myPatternsTable.removeAssociation(matcher, stdFileType.fileType);
      }
      fileTypeChanged(stdFileType.fileType, ApplicationManager.getApplication().isUnitTestMode());
    });
  }

  private void fileTypeChanged(@NotNull FileType stdFileType, boolean later) {
    if (later) {
      //avoid PCE when reloading file type
      ApplicationManager.getApplication().invokeLater(
        () -> WriteAction.run(() -> fireFileTypesChanged(stdFileType, null)));
    } else {
      fireFileTypesChanged(stdFileType, null);
    }
  }

  @VisibleForTesting
  void initStandardFileTypes() {
    loadFileTypeBeans();

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
      public void consume(@NotNull final FileType fileType, final FileNameMatcher @NotNull ... matchers) {
        register(fileType, new ArrayList<>(Arrays.asList(matchers)));
      }

      @Override
      public FileType getStandardFileTypeByName(@NotNull final String name) {
        final StandardFileType type = myStandardFileTypes.get(name);
        return type != null ? type.fileType : null;
      }

      private void register(@NotNull FileType fileType, @NotNull List<FileNameMatcher> fileNameMatchers) {
        instantiatePendingFileTypeByName(fileType.getName());
        for (FileNameMatcher matcher : fileNameMatchers) {
          FileTypeBean pendingTypeByMatcher = myPendingAssociations.findAssociatedFileType(matcher);
          if (pendingTypeByMatcher != null) {
            PluginId id = pendingTypeByMatcher.getPluginId();
            if (id == null || PluginManagerCore.CORE_ID == id) {
              instantiateFileTypeBean(pendingTypeByMatcher);
            }
          }
        }

        final StandardFileType type = myStandardFileTypes.get(fileType.getName());
        if (type != null) {
          type.matchers.addAll(fileNameMatchers);
        }
        else {
          myStandardFileTypes.put(fileType.getName(), new StandardFileType(fileType, fileNameMatchers));
        }
      }
    };

    //noinspection deprecation
    FileTypeFactory.FILE_TYPE_FACTORY_EP.processWithPluginDescriptor((factory, pluginDescriptor) -> {
      try {
        factory.createFileTypes(consumer);
      }
      catch (ProcessCanceledException | StartupAbortedException e) {
        throw e;
      }
      catch (Throwable e) {
        throw new StartupAbortedException("Cannot create file types", new PluginException(e, pluginDescriptor.getPluginId()));
      }
    });

    for (StandardFileType pair : myStandardFileTypes.values()) {
      registerFileTypeWithoutNotification(pair.fileType, pair.matchers, Collections.emptyList(), true);
    }

    try {
      URL defaultFileTypesUrl = FileTypeManagerImpl.class.getResource("/defaultFileTypes.xml");
      if (defaultFileTypesUrl != null) {
        Element defaultFileTypesElement = JDOMUtil.load(URLUtil.openStream(defaultFileTypesUrl));
        for (Element e : defaultFileTypesElement.getChildren()) {
          if ("filetypes".equals(e.getName())) {
            for (Element element : e.getChildren(ELEMENT_FILETYPE)) {
              String fileTypeName = element.getAttributeValue(ATTRIBUTE_NAME);
              if (myPendingFileTypes.get(fileTypeName) != null) continue;
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

  private void loadFileTypeBeans() {
    final List<FileTypeBean> fileTypeBeans = EP_NAME.getExtensionList();

    for (FileTypeBean bean : fileTypeBeans) {
      initializeMatchers(bean);
    }

    for (FileTypeBean bean : fileTypeBeans) {
      if (bean.implementationClass == null) continue;

      if (myPendingFileTypes.containsKey(bean.name)) {
        LOG.error(new PluginException("Trying to override already registered file type " + bean.name, bean.getPluginId()));
        continue;
      }

      myPendingFileTypes.put(bean.name, bean);
      for (FileNameMatcher matcher : bean.getMatchers()) {
        myPendingAssociations.addAssociation(matcher, bean);
      }
    }

    // Register additional extensions for file types
    for (FileTypeBean bean : fileTypeBeans) {
      if (bean.implementationClass != null) continue;
      FileTypeBean oldBean = myPendingFileTypes.get(bean.name);
      if (oldBean == null) {
        LOG.error(new PluginException("Trying to add extensions to non-registered file type " + bean.name, bean.getPluginId()));
        continue;
      }
      oldBean.addMatchers(bean.getMatchers());
      for (FileNameMatcher matcher : bean.getMatchers()) {
        myPendingAssociations.addAssociation(matcher, oldBean);
      }
    }
  }

  private static void initializeMatchers(@NotNull FileTypeBean bean) {
    bean.addMatchers(ContainerUtil.concat(
      parse(bean.extensions),
      parse(bean.fileNames, token -> new ExactFileNameMatcher(token)),
      parse(bean.fileNamesCaseInsensitive, token -> new ExactFileNameMatcher(token, true)),
      parse(bean.patterns, token -> FileNameMatcherFactory.getInstance().createMatcher(token))));
  }

  private void instantiatePendingFileTypes() {
    final Collection<FileTypeBean> fileTypes = new ArrayList<>(withReadLock(() -> myPendingFileTypes.values()));
    for (FileTypeBean fileTypeBean : fileTypes) {
      mergeOrInstantiateFileTypeBean(fileTypeBean);
    }
  }

  @NotNull
  private FileType mergeOrInstantiateFileTypeBean(@NotNull FileTypeBean fileTypeBean) {
    StandardFileType type = withReadLock(() -> myStandardFileTypes.get(fileTypeBean.name));
    if (type == null) {
      return instantiateFileTypeBean(fileTypeBean);
    }
    type.matchers.addAll(fileTypeBean.getMatchers());
    for (FileNameMatcher matcher : fileTypeBean.getMatchers()) {
      myPatternsTable.addAssociation(matcher, type.fileType);
    }
    return type.fileType;
  }

  private FileType instantiateFileTypeBean(@NotNull FileTypeBean bean) {
    Lock writeLock = myPendingInitializationLock.writeLock();
    writeLock.lock();
    try {
      FileType fileType;

      if (!myPendingFileTypes.containsKey(bean.name)) {
        fileType = mySchemeManager.findSchemeByName(bean.name);
        if (fileType != null && !(fileType instanceof AbstractFileType)) {
          return fileType;
        }
      }

      PluginId pluginId = bean.getPluginDescriptor().getPluginId();
      try {
        @SuppressWarnings("unchecked")
        Class<FileType> beanClass =
          (Class<FileType>)Class.forName(bean.implementationClass, true, bean.getPluginDescriptor().getPluginClassLoader());
        if (bean.fieldName != null) {
          Field field = beanClass.getDeclaredField(bean.fieldName);
          field.setAccessible(true);
          fileType = (FileType)field.get(null);
        }
        else {
          // uncached - cached by FileTypeManagerImpl and not by bean
          fileType = ReflectionUtil.newInstance(beanClass, false);
        }
      }
      catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
        LOG.error(new PluginException(e, pluginId));
        return null;
      }

      if (!fileType.getName().equals(bean.name)) {
        LOG.error(new PluginException("Incorrect name specified in <fileType>, should be " + fileType.getName() + ", actual " + bean.name,
                                      pluginId));
      }
      if (fileType instanceof LanguageFileType) {
        final LanguageFileType languageFileType = (LanguageFileType)fileType;
        String expectedLanguage = languageFileType.isSecondary() ? null : languageFileType.getLanguage().getID();
        if (!Objects.equals(bean.language, expectedLanguage)) {
          LOG.error(new PluginException("Incorrect language specified in <fileType> for " +
                                        fileType.getName() +
                                        ", should be " +
                                        expectedLanguage +
                                        ", actual " +
                                        bean.language, pluginId));
        }
      }

      final StandardFileType standardFileType = new StandardFileType(fileType, bean.getMatchers());
      myStandardFileTypes.put(bean.name, standardFileType);
      List<String> hashBangs = bean.hashBangs == null ? Collections.emptyList() : StringUtil.split(bean.hashBangs, ";");
      registerFileTypeWithoutNotification(fileType, standardFileType.matchers, hashBangs, true);

      myPendingAssociations.removeAllAssociations(bean);
      myPendingFileTypes.remove(bean.name);

      return fileType;
    } finally {
      writeLock.unlock();
    }
  }

  @TestOnly
  static volatile boolean toLog = SystemProperties.is("trace.file.type.manager");
  static boolean toLog() {
    return toLog;
  }

  static void log(@NonNls String message) {
    LOG.debug(message + " - "+Thread.currentThread());
  }

  @TestOnly
  public void drainReDetectQueue() {
    myDetectionService.drainReDetectQueue();
  }

  @TestOnly
  @NotNull
  Collection<VirtualFile> dumpReDetectQueue() {
    return myDetectionService.dumpReDetectQueue();
  }

  @TestOnly
  static void reDetectAsync(boolean enable) {
    FileTypeDetectionService.reDetectAsync(enable);
  }

  @Override
  @NotNull
  public FileType getStdFileType(@NotNull @NonNls String name) {
    StandardFileType stdFileType;
    instantiatePendingFileTypeByName(name);
    stdFileType = withReadLock(() -> myStandardFileTypes.get(name));
    return stdFileType != null ? stdFileType.fileType : PlainTextFileType.INSTANCE;
  }

  private void instantiatePendingFileTypeByName(@NonNls @NotNull String name) {
    final FileTypeBean bean = withReadLock(() -> myPendingFileTypes.get(name));
    if (bean != null) {
      instantiateFileTypeBean(bean);
    }
  }

  @Override
  public void initializeComponent() {
    if (!myUnresolvedMappings.isEmpty()) {
      instantiatePendingFileTypes();
    }

    if (!myUnresolvedMappings.isEmpty()) {
      for (StandardFileType pair : myStandardFileTypes.values()) {
        registerReDetectedMappings(pair);
      }
    }

    // resolve unresolved mappings initialized before certain plugin initialized
    if (!myUnresolvedMappings.isEmpty()) {
      for (StandardFileType pair : myStandardFileTypes.values()) {
        bindUnresolvedMappings(pair.fileType);
      }
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
    FileTypeBean pendingFileType = withReadLock(() -> myPendingAssociations.findAssociatedFileType(fileName));
    if (pendingFileType != null) {
      return ObjectUtils.notNull(instantiateFileTypeBean(pendingFileType), UnknownFileType.INSTANCE);
    }
    FileType type = withReadLock(() -> myPatternsTable.findAssociatedFileType(fileName));
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
    return getFileTypeByFile(file, null);
  }

  @Override
  @NotNull
  public FileType getFileTypeByFile(@NotNull VirtualFile file, byte @Nullable [] content) {
    FileType overriddenFileType = FileTypeOverrider.EP_NAME.computeSafeIfAny(overrider -> overrider.getOverriddenFileType(file));
    if (overriddenFileType != null) {
      return overriddenFileType;
    }

    FileType fileType = getByFile(file);
    if (!(file instanceof StubVirtualFile)) {
      if (fileType == null) {
        return myDetectionService.getOrDetectFromContent(file, content);
      }
      if (mightBeReplacedByDetectedFileType(fileType) && FileTypeDetectionService.isDetectable(file)) {
        FileType detectedFromContent = myDetectionService.getOrDetectFromContent(file, content);
        // unknown file type means that it was detected as binary, it's better to keep it binary
        if (detectedFromContent != PlainTextFileType.INSTANCE) {
          return detectedFromContent;
        }
      }
    }
    return ObjectUtils.notNull(fileType, UnknownFileType.INSTANCE);
  }

  static boolean mightBeReplacedByDetectedFileType(@NotNull FileType fileType) {
    return fileType instanceof PlainTextLikeFileType && fileType.isReadOnly();
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

  @Override
  public FileType findFileTypeByName(@NotNull String fileTypeName) {
    FileType type = getStdFileType(fileTypeName);
    // TODO: Abstract file types are not std one, so need to be restored specially,
    // currently there are 6 of them and restoration does not happen very often so just iteration is enough
    if (type != PlainTextFileType.INSTANCE || fileTypeName.equals(type.getName())) {
      return type;
    }
    for (FileType fileType: mySchemeManager.getAllSchemes()) {
      if (fileTypeName.equals(fileType.getName())) {
        return fileType;
      }
    }
    return null;
  }

  @Override
  public LanguageFileType findFileTypeByLanguage(@NotNull Language language) {
    FileTypeBean bean = withReadLock(() -> {
      for (FileTypeBean b : myPendingFileTypes.values()) {
        if (language.getID().equals(b.language)) {
          return b;
        }
      }
      return null;
    });
    if (bean != null) {
      return (LanguageFileType)instantiateFileTypeBean(bean);
    }

    // Do not use getRegisteredFileTypes() to avoid instantiating all pending file types
    return withReadLock(() -> language.findMyFileType(mySchemeManager.getAllSchemes().toArray(FileType.EMPTY_ARRAY)));
  }

  @Override
  @NotNull
  public FileType getFileTypeByExtension(@NotNull String extension) {
    final FileTypeBean pendingFileType = withReadLock(() -> myPendingAssociations.findByExtension(extension));
    if (pendingFileType != null) {
      return ObjectUtils.notNull(instantiateFileTypeBean(pendingFileType), UnknownFileType.INSTANCE);
    }
    FileType type = withReadLock(() -> myPatternsTable.findByExtension(extension));
    return ObjectUtils.notNull(type, UnknownFileType.INSTANCE);
  }

  @Override
  @Deprecated
  public void registerFileType(@NotNull FileType fileType) {
    registerFileType(fileType, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  @Override
  public void registerFileType(@NotNull final FileType type, @NotNull final List<? extends FileNameMatcher> defaultAssociations) {
    DeprecatedMethodException.report("Use fileType extension instead.");
    ApplicationManager.getApplication().runWriteAction(() -> {
      fireBeforeFileTypesChanged();
      registerFileTypeWithoutNotification(type, defaultAssociations, Collections.emptyList(), true);
      fireFileTypesChanged(type, null);
    });
  }

  @Override
  public void unregisterFileType(@NotNull final FileType fileType) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      fireBeforeFileTypesChanged();
      unregisterFileTypeWithoutNotification(fileType);
      myStandardFileTypes.remove(fileType.getName());
      if (fileType instanceof LanguageFileType) {
        Language.unregisterLanguage(((LanguageFileType) fileType).getLanguage());
      }
      fireFileTypesChanged(null, fileType);
    });
  }

  private void unregisterFileTypeWithoutNotification(@NotNull FileType fileType) {
    CachedFileType.remove(fileType);
    myPatternsTable.removeAllAssociations(fileType);
    myInitialAssociations.removeAllAssociations(fileType);
    mySchemeManager.removeScheme(fileType);
    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      final FileTypeIdentifiableByVirtualFile fakeFileType = (FileTypeIdentifiableByVirtualFile)fileType;
      mySpecialFileTypes = ArrayUtil.remove(mySpecialFileTypes, fakeFileType, FileTypeIdentifiableByVirtualFile.ARRAY_FACTORY);
    }
  }

  @Override
  public FileType @NotNull [] getRegisteredFileTypes() {
    instantiatePendingFileTypes();
    Collection<FileType> fileTypes = mySchemeManager.getAllSchemes();
    return fileTypes.toArray(FileType.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public String getExtension(@NotNull String fileName) {
    return FileUtilRt.getExtension(fileName);
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
    Set<String> tempSet = new ObjectOpenHashSet<>();
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
  public String @NotNull [] getAssociatedExtensions(@NotNull FileType type) {
    instantiatePendingFileTypeByName(type.getName());

    return withReadLock(() -> myPatternsTable.getAssociatedExtensions(type));
  }

  @Override
  @NotNull
  public List<FileNameMatcher> getAssociations(@NotNull FileType type) {
    instantiatePendingFileTypeByName(type.getName());

    return withReadLock(() -> myPatternsTable.getAssociations(type));
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
    FileTypeEvent event = new FileTypeEvent(this, null, null);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).beforeFileTypesChanged(event);
  }


  @Override
  public void fireFileTypesChanged() {
    fireFileTypesChanged(null, null);
  }

  private void fireFileTypesChanged(@Nullable FileType addedFileType, @Nullable FileType removedFileType) {
    myDetectionService.clearCaches();
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).fileTypesChanged(new FileTypeEvent(this, addedFileType, removedFileType));
  }

  private final Map<FileTypeListener, MessageBusConnection> myAdapters = new HashMap<>();

  @Override
  public void addFileTypeListener(@NotNull FileTypeListener listener) {
    final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
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
  public void loadState(@NotNull Element state) {
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

    myDetectionService.loadState(state);
  }

  private void unignoreMask(@NonNls @NotNull final String maskToRemove) {
    final Set<String> masks = new LinkedHashSet<>(myIgnoredPatterns.getIgnoreMasks());
    masks.remove(maskToRemove);

    myIgnoredPatterns.clearPatterns();
    for (final String each : masks) {
      myIgnoredPatterns.addIgnoreMask(each);
    }
  }

  private void readGlobalMappings(@NotNull Element e, boolean isAddToInit) {
    for (Pair<FileNameMatcher, String> association : AbstractFileType.readAssociations(e)) {
      String fileTypeName = association.getSecond();
      FileType type = getFileTypeByName(fileTypeName);
      FileNameMatcher matcher = association.getFirst();
      final FileTypeBean pendingFileTypeBean = myPendingAssociations.findAssociatedFileType(matcher);
      if (pendingFileTypeBean != null) {
        instantiateFileTypeBean(pendingFileTypeBean);
      }

      if (type != null) {
        if (PlainTextFileType.INSTANCE == type) {
          FileType newFileType = myPatternsTable.findAssociatedFileType(matcher);
          if (newFileType != null && newFileType != PlainTextFileType.INSTANCE && newFileType != UnknownFileType.INSTANCE) {
            myRemovedMappingTracker.add(matcher, newFileType.getName(), false);
          }
        }
        associate(type, matcher, false);
        if (isAddToInit) {
          myInitialAssociations.addAssociation(matcher, type);
        }
      }
      else {
        myUnresolvedMappings.put(matcher, fileTypeName);
      }
    }

    for (Map.Entry<String, FileType> entry : readHashBangs(e).entrySet()) {
      String hashBang = entry.getKey();
      FileType fileType = entry.getValue();
      myPatternsTable.addHashBangPattern(hashBang, fileType);
      if (isAddToInit) {
        myInitialAssociations.addHashBangPattern(hashBang, fileType);
      }
    }

    myRemovedMappingTracker.load(e);
    for (RemovedMappingTracker.RemovedMapping mapping : myRemovedMappingTracker.getRemovedMappings()) {
      FileType fileType = getFileTypeByName(mapping.getFileTypeName());
      if (fileType != null) {
        removeAssociation(fileType, mapping.getFileNameMatcher(), false);
      }
    }
  }

  private @NotNull Map<String, FileType> readHashBangs(@NotNull Element e) {
    List<Element> children = e.getChildren("hashBang");
    Map<String, FileType> result = CollectionFactory.createMap(children.size());
    for (Element hashBangTag : children) {
      String typeName = hashBangTag.getAttributeValue("type");
      String hashBangPattern = hashBangTag.getAttributeValue("value");
      FileType fileType = typeName == null ? null : getFileTypeByName(typeName);
      if (hashBangPattern == null || fileType == null) continue;
      result.put(hashBangPattern, fileType);
    }
    return result;
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
      String[] strings = ArrayUtilRt.toStringArray(masks);
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
      notExternalizableFileTypes.sort(Comparator.comparing(FileType::getName));
      for (FileType type : notExternalizableFileTypes) {
        writeExtensionsMap(map, type, true);
      }
    }

    // https://youtrack.jetbrains.com/issue/IDEA-138366
    myRemovedMappingTracker.save(map);

    if (!myUnresolvedMappings.isEmpty()) {
      List<Map.Entry<FileNameMatcher, String>> entries = new ArrayList<>(myUnresolvedMappings.entrySet());
      entries.sort(Comparator.comparing(e->e.getKey().getPresentableString()));

      for (Map.Entry<FileNameMatcher, String> entry : entries) {
        FileNameMatcher fileNameMatcher = entry.getKey();
        String typeName = entry.getValue();
        Element content = AbstractFileType.writeMapping(typeName, fileNameMatcher, true);
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
    Set<FileNameMatcher> defaultAssociations = new ObjectOpenHashSet<>(myInitialAssociations.getAssociations(type));

    for (FileNameMatcher matcher : associations) {
      boolean isDefaultAssociationContains = defaultAssociations.remove(matcher);
      if (!isDefaultAssociationContains && shouldSave(type)) {
        Element content = AbstractFileType.writeMapping(type.getName(), matcher, specifyTypeName);
        if (content != null) {
          map.addContent(content);
        }
      }
    }
    List<String> readOnlyHashBangs = myInitialAssociations.getHashBangPatterns(type);
    List<String> hashBangPatterns = myPatternsTable.getHashBangPatterns(type);
    hashBangPatterns.sort(Comparator.naturalOrder());
    for (String hashBangPattern : hashBangPatterns) {
      if (!readOnlyHashBangs.contains(hashBangPattern)) {
        Element hashBangTag = new Element("hashBang");
        hashBangTag.setAttribute("value", hashBangPattern);
        hashBangTag.setAttribute("type", type.getName());
        map.addContent(hashBangTag);
      }
    }
    myRemovedMappingTracker.saveRemovedMappingsForFileType(map, type.getName(), defaultAssociations, specifyTypeName);
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  @Nullable
  private FileType getFileTypeByName(@NotNull String name) {
    instantiatePendingFileTypeByName(name);

    return withReadLock(() -> mySchemeManager.findSchemeByName(name));
  }

  @NotNull
  private static List<FileNameMatcher> parse(@Nullable String semicolonDelimited) {
    return parse(semicolonDelimited, token -> new ExtensionFileNameMatcher(token));
  }

  @NotNull
  private static List<FileNameMatcher> parse(@Nullable String semicolonDelimited, Function<? super String, ? extends FileNameMatcher> matcherFactory) {
    if (semicolonDelimited == null) {
      return Collections.emptyList();
    }

    StringTokenizer tokenizer = new StringTokenizer(semicolonDelimited, FileTypeConsumer.EXTENSION_DELIMITER, false);
    List<FileNameMatcher> list = new ArrayList<>(semicolonDelimited.length() / "py;".length());
    while (tokenizer.hasMoreTokens()) {
      list.add(matcherFactory.fun(tokenizer.nextToken().trim()));
    }
    return list;
  }

  /**
   * Registers a standard file type. Doesn't notifyListeners any change events.
   */
  private void registerFileTypeWithoutNotification(@NotNull FileType fileType, @NotNull List<? extends FileNameMatcher> matchers, @NotNull List<String> hasBangPatterns, boolean addScheme) {
    if (addScheme) {
      mySchemeManager.addScheme(fileType);
    }
    for (FileNameMatcher matcher : matchers) {
      myPatternsTable.addAssociation(matcher, fileType);
      myInitialAssociations.addAssociation(matcher, fileType);
    }
    for (String hashBang : hasBangPatterns) {
      myPatternsTable.addHashBangPattern(hashBang, fileType);
      myInitialAssociations.addHashBangPattern(hashBang, fileType);
    }

    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      mySpecialFileTypes = ArrayUtil.append(mySpecialFileTypes, (FileTypeIdentifiableByVirtualFile)fileType, FileTypeIdentifiableByVirtualFile.ARRAY_FACTORY);
    }
  }

  private void bindUnresolvedMappings(@NotNull FileType fileType) {
    for (FileNameMatcher matcher : new ObjectOpenHashSet<>(myUnresolvedMappings.keySet())) {
      String name = myUnresolvedMappings.get(matcher);
      if (Objects.equals(name, fileType.getName())) {
        myPatternsTable.addAssociation(matcher, fileType);
        myUnresolvedMappings.remove(matcher);
      }
    }

    for (FileNameMatcher matcher : myRemovedMappingTracker.getMappingsForFileType(fileType.getName())) {
      removeAssociation(fileType, matcher, false);
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
      type = new UserBinaryFileType();
    }
    else {
      SyntaxTable table = AbstractFileType.readSyntaxTable(element);
      type = new AbstractFileType(table);
      ((AbstractFileType)type).initSupport();
    }

    setFileTypeAttributes((UserFileType)type, fileTypeName, fileTypeDescr, iconPath);
    registerFileTypeWithoutNotification(type, parse(extensionsStr), Collections.emptyList(), isDefault);

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

        for (RemovedMappingTracker.RemovedMapping removedAssociation : RemovedMappingTracker.readRemovedMappings(extensions)) {
          removeAssociation(type, removedAssociation.getFileNameMatcher(), false);
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
      if (myPendingAssociations.findByExtension(extension) == null && getFileTypeByExtension(extension) == UnknownFileType.INSTANCE) {
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

  private static void setFileTypeAttributes(@NotNull UserFileType<?> fileType, @Nullable String name, @Nullable String description, @Nullable String iconPath) {
    if (!StringUtil.isEmptyOrSpaces(iconPath)) {
      fileType.setIconPath(iconPath);
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
  FileTypeAssocTable<FileType> getExtensionMap() {
    instantiatePendingFileTypes();

    return withReadLock(() -> myPatternsTable);
  }

  void setPatternsTable(@NotNull Set<? extends FileType> fileTypes, @NotNull FileTypeAssocTable<FileType> assocTable) {
    Map<FileNameMatcher, FileType> removedMappings = getExtensionMap().getRemovedMappings(assocTable, fileTypes);
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

    myRemovedMappingTracker.removeMatching((matcher, fileTypeName) -> {
      FileType fileType = getFileTypeByName(fileTypeName);
      return fileType != null && assocTable.isAssociatedWith(fileType, matcher);
    });
    for (Map.Entry<FileNameMatcher, FileType> entry : removedMappings.entrySet()) {
      myRemovedMappingTracker.add(entry.getKey(), entry.getValue().getName(), true);
    }
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
      registerReDetectedMapping(fileType.getName(), matcher);
      if (matcher instanceof ExtensionFileNameMatcher) {
        // also check exact file name matcher
        ExtensionFileNameMatcher extMatcher = (ExtensionFileNameMatcher)matcher;
        registerReDetectedMapping(fileType.getName(), new ExactFileNameMatcher("." + extMatcher.getExtension()));
      }
    }
  }

  private void registerReDetectedMapping(@NotNull String fileTypeName, @NotNull FileNameMatcher matcher) {
    String typeName = myUnresolvedMappings.get(matcher);
    if (typeName != null && !typeName.equals(fileTypeName)) {
      if (!myRemovedMappingTracker.hasRemovedMapping(matcher)) {
        myRemovedMappingTracker.add(matcher, fileTypeName, false);
      }
      myUnresolvedMappings.remove(matcher);
    }
  }

  private <T, E extends Throwable> T withReadLock(ThrowableComputable<T, E> computable) throws E {
    return ConcurrencyUtil.withLock(myPendingInitializationLock.readLock(), computable);
  }

  @NotNull
  RemovedMappingTracker getRemovedMappingTracker() {
    return myRemovedMappingTracker;
  }

  @TestOnly
  void clearForTests() {
    for (StandardFileType fileType : myStandardFileTypes.values()) {
      myPatternsTable.removeAllAssociations(fileType.fileType);
    }
    for (FileType type : myDefaultTypes) {
      myPatternsTable.removeAllAssociations(type);
    }
    myStandardFileTypes.clear();
    myDefaultTypes.clear();
    myUnresolvedMappings.clear();
    myRemovedMappingTracker.clear();
    for (FileTypeBean bean : myPendingFileTypes.values()) {
      myPendingAssociations.removeAllAssociations(bean);
    }
    myPendingFileTypes.clear();
    mySchemeManager.setSchemes(Collections.emptyList());
  }
}
