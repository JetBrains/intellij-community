// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginConflictReporter;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.StartupAbortedException;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.*;
import com.intellij.openapi.options.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserExtensionsStateService;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.*;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

@State(name = "FileTypeManager", storages = @Storage("filetypes.xml"), additionalExportDirectory = FileTypeManagerImpl.FILE_SPEC)
public class FileTypeManagerImpl extends FileTypeManagerEx implements PersistentStateComponent<Element> {
  static final ExtensionPointName<FileTypeBean> EP_NAME = new ExtensionPointName<>("com.intellij.fileType");
  private static final Logger LOG = Logger.getInstance(FileTypeManagerImpl.class);

  // You must update all existing default configurations accordingly
  private static final int VERSION = 18;
  private static final ThreadLocal<Pair<VirtualFile, FileType>> FILE_TYPE_FIXED_TEMPORARILY = new ThreadLocal<>();

  // must be sorted
  @SuppressWarnings("SpellCheckingInspection")
  static final String DEFAULT_IGNORED = "*.pyc;*.pyo;*.rbc;*.yarb;*~;.DS_Store;.git;.hg;.svn;CVS;__pycache__;_svn;vssver.scc;vssver2.scc;";
  @NonNls private static final String ELEMENT_EXTENSION_MAP = "extensionMap";

  private final Set<FileTypeWithDescriptor> myDefaultTypes = CollectionFactory.createSmallMemoryFootprintSet();
  private final FileTypeDetectionService myDetectionService;
  private FileTypeIdentifiableByVirtualFile[] mySpecialFileTypes = FileTypeIdentifiableByVirtualFile.EMPTY_ARRAY;

  FileTypeAssocTable<FileTypeWithDescriptor> myPatternsTable = new FileTypeAssocTable<>();
  private final IgnoredPatternSet myIgnoredPatterns = new IgnoredPatternSet();
  private final IgnoredFileCache myIgnoredFileCache = new IgnoredFileCache(myIgnoredPatterns);

  private final FileTypeAssocTable<FileType> myInitialAssociations = new FileTypeAssocTable<>();
  private final Map<FileNameMatcher, String> myUnresolvedMappings = new HashMap<>();
  private final RemovedMappingTracker myRemovedMappingTracker = new RemovedMappingTracker();
  private final ConflictingFileTypeMappingTracker
    myConflictingMappingTracker = new ConflictingFileTypeMappingTracker(myRemovedMappingTracker);

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
    @NotNull private final PluginDescriptor pluginDescriptor;

    private StandardFileType(@NotNull FileType fileType, @NotNull PluginDescriptor pluginDescriptor, @NotNull List<? extends FileNameMatcher> matchers) {
      this.fileType = fileType;
      this.pluginDescriptor = pluginDescriptor;
      this.matchers = new ArrayList<>(matchers);
    }
  }

  private final Map<String, StandardFileType> myStandardFileTypes = new LinkedHashMap<>();
  @NonNls
  private static final String[] FILE_TYPES_WITH_PREDEFINED_EXTENSIONS = {"JSP", "JSPX", "DTD", "HTML", "Properties", "XHTML"};
  private final SchemeManager<FileTypeWithDescriptor> mySchemeManager;
  static class FileTypeWithDescriptor implements Scheme {
    private static final PluginDescriptor WILD_CARD = new DefaultPluginDescriptor("WILD_CARD");
    final @NotNull FileType fileType;
    final @NotNull PluginDescriptor pluginDescriptor;

    FileTypeWithDescriptor(@NotNull FileType fileType, @NotNull PluginDescriptor pluginDescriptor) {
      this.fileType = fileType;
      this.pluginDescriptor = pluginDescriptor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FileTypeWithDescriptor that = (FileTypeWithDescriptor)o;

      return fileType.equals(that.fileType);
    }

    @Override
    public int hashCode() {
      return fileType.hashCode();
    }

    @Override
    public String toString() {
      return fileType +" from '"+(pluginDescriptor==WILD_CARD ? "*" : pluginDescriptor)+"'";
    }

    // equals to all FileTypeWithDescriptor with this fileType
    static @NotNull FileTypeWithDescriptor allFor(FileType fileType) {
      return new FileTypeWithDescriptor(fileType, WILD_CARD);
    }

    @Override
    public @NotNull String getName() {
      return fileType.getName();
    }
  }
  @NonNls
  static final String FILE_SPEC = "filetypes";

  public FileTypeManagerImpl() {
    NonLazySchemeProcessor<FileTypeWithDescriptor, FileTypeWithDescriptor> abstractTypesProcessor = new NonLazySchemeProcessor<>() {
      @NotNull
      @Override
      public FileTypeWithDescriptor readScheme(@NotNull Element element, boolean duringLoad) {
        if (!duringLoad) {
          fireBeforeFileTypesChanged();
        }
        IdeaPluginDescriptor pluginDescriptor = coreIdeaPluginDescriptor();
        AbstractFileType type = (AbstractFileType)loadFileType("filetypes.xml", element, pluginDescriptor, false);
        if (!duringLoad) {
          fireFileTypesChanged(type, null);
        }
        return new FileTypeWithDescriptor(type, pluginDescriptor);
      }

      @NotNull
      @Override
      public SchemeState getState(@NotNull FileTypeWithDescriptor ftd) {
        if (!(ftd.fileType instanceof AbstractFileType) || !shouldSave(ftd.fileType)) {
          return SchemeState.NON_PERSISTENT;
        }
        if (!myDefaultTypes.contains(ftd)) {
          return SchemeState.POSSIBLY_CHANGED;
        }
        return ((AbstractFileType)ftd.fileType).isModified() ? SchemeState.POSSIBLY_CHANGED : SchemeState.NON_PERSISTENT;
      }

      @NotNull
      @Override
      public Element writeScheme(@NotNull FileTypeWithDescriptor ftd) {
        Element root = new Element(ELEMENT_FILETYPE);

        AbstractFileType fileType = (AbstractFileType)ftd.fileType;
        root.setAttribute("binary", String.valueOf(fileType.isBinary()));
        if (!Strings.isEmpty(fileType.getDefaultExtension())) {
          root.setAttribute("default_extension", fileType.getDefaultExtension());
        }
        root.setAttribute(ATTRIBUTE_DESCRIPTION, fileType.getDescription());
        root.setAttribute(ATTRIBUTE_NAME, fileType.getName());

        fileType.writeExternal(root);

        Element map = new Element(ELEMENT_EXTENSION_MAP);
        writeExtensionsMap(map, ftd, false);
        if (!map.getChildren().isEmpty()) {
          root.addContent(map);
        }
        return root;
      }

      @Override
      public void onSchemeDeleted(@NotNull FileTypeWithDescriptor scheme) {
        GuiUtils.invokeLaterIfNeeded(() -> {
          Application app = ApplicationManager.getApplication();
          app.runWriteAction(() -> fireBeforeFileTypesChanged());
          myPatternsTable.removeAllAssociations(scheme);
          app.runWriteAction(() -> fireFileTypesChanged(null, scheme.fileType));
        }, ModalityState.NON_MODAL);
      }
    };
    mySchemeManager = SchemeManagerFactory.getInstance().create(FILE_SPEC, abstractTypesProcessor);

    // this should be done BEFORE reading state
    initStandardFileTypes();

    myDetectionService = new FileTypeDetectionService(this);

    myIgnoredPatterns.setIgnoreMasks(DEFAULT_IGNORED);

    EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull FileTypeBean fileTypeBean, @NotNull PluginDescriptor pluginDescriptor) {
        fireBeforeFileTypesChanged();
        initializeMatchers(pluginDescriptor, fileTypeBean);
        FileTypeBean pendingFileTypeBean = withReadLock(() -> myPendingFileTypes.get(fileTypeBean.name));
        if (pendingFileTypeBean != null) {
          // some new matcher is being added to already existing but not-yet instantiated file type
          instantiateFileTypeBean(pendingFileTypeBean);
        }
        FileType fileType = mergeOrInstantiateFileTypeBean(fileTypeBean);

        fileTypeChanged(fileType, ApplicationManager.getApplication().isUnitTestMode());
      }

      @Override
      public void extensionRemoved(@NotNull FileTypeBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        if (extension.implementationClass != null) {
          FileType fileType = findFileTypeByName(extension.name);
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
    }, null);
  }

  @NotNull
  static IdeaPluginDescriptor coreIdeaPluginDescriptor() {
    return PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID);
  }

  private void unregisterMatchers(@NotNull StandardFileType stdFileType, @NotNull FileTypeBean extension) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      stdFileType.matchers.removeAll(extension.getMatchers());
      for (FileNameMatcher matcher : extension.getMatchers()) {
        myPatternsTable.removeAssociation(matcher, descriptorForStandard(stdFileType));
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
  @NotNull
  List<ConflictingFileTypeMappingTracker.ResolveConflictResult> initStandardFileTypes() {
    List<ConflictingFileTypeMappingTracker.ResolveConflictResult> notificationsShown = new ArrayList<>();
    instantiatePendingFileTypes();

    for (Map.Entry<String, StandardFileType> entry : myStandardFileTypes.entrySet()) {
      String name = entry.getKey();
      StandardFileType stdType = entry.getValue();
      FileType type = stdType.fileType;
      FileTypeWithDescriptor ftd = descriptorForStandard(stdType);
      for (FileNameMatcher matcher : stdType.matchers) {
        removeAssociation(ftd, matcher, false);
      }
      mySchemeManager.removeScheme(name);
      removeFromDuplicates(type, ftd.pluginDescriptor);
    }
    myStandardFileTypes.clear();
    for (FileTypeWithDescriptor ftd : myDefaultTypes) {
      String name = ftd.getName();
      FileType defaultType = ftd.fileType;
      List<FileNameMatcher> matchers = getAssociations(defaultType);
      for (FileNameMatcher matcher : matchers) {
        removeAssociation(ftd, matcher, false);
      }
      mySchemeManager.removeScheme(name);
      removeFromDuplicates(defaultType, ftd.pluginDescriptor);
    }
    myDefaultTypes.clear();
    loadFileTypeBeans();

    //noinspection deprecation
    FileTypeFactory.FILE_TYPE_FACTORY_EP.processWithPluginDescriptor((factory, pluginDescriptor) -> {
      FileTypeConsumer consumer = new PluginFileTypeConsumer(pluginDescriptor);

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
      if (mySchemeManager.findSchemeByName(pair.fileType.getName()) == null) {
        notificationsShown.addAll(registerFileTypeWithoutNotification(pair.fileType, pair.pluginDescriptor, pair.matchers, true));
      }
    }

    try {
      InputStream defaultFileTypeStream = FileTypeManagerImpl.class.getClassLoader().getResourceAsStream("defaultFileTypes.xml");
      if (defaultFileTypeStream != null) {
        Element defaultFileTypesElement = JDOMUtil.load(defaultFileTypeStream);
        IdeaPluginDescriptor coreIdeaPluginDescriptor = coreIdeaPluginDescriptor();
        for (Element e : defaultFileTypesElement.getChildren()) {
          if ("filetypes".equals(e.getName())) {
            for (Element element : e.getChildren(ELEMENT_FILETYPE)) {
              String fileTypeName = element.getAttributeValue(ATTRIBUTE_NAME);
              if (myPendingFileTypes.get(fileTypeName) == null) {
                loadFileType("defaultFileTypes.xml", element, coreIdeaPluginDescriptor, true);
              }
            }
          }
          else if (ELEMENT_EXTENSION_MAP.equals(e.getName())) {
            readGlobalMappings(e, true);
          }
        }

        if (PlatformUtils.isIdeaCommunity()) {
          Element extensionMap = new Element(ELEMENT_EXTENSION_MAP);
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
    return notificationsShown;
  }

  private void removeFromDuplicates(@NotNull FileType type, @NotNull PluginDescriptor pluginDescriptor) {
    names.computeIfAbsent(pluginDescriptor, __ -> new ConcurrentHashMap<>()).remove(type.getName());
    displayNames.computeIfAbsent(pluginDescriptor, __ -> new ConcurrentHashMap<>()).remove(type.getDisplayName());
    descriptions.computeIfAbsent(pluginDescriptor, __ -> new ConcurrentHashMap<>()).remove(type.getDescription());
  }

  private void loadFileTypeBeans() {
    List<FileTypeBean> fileTypeBeans = EP_NAME.getExtensionList();

    for (FileTypeBean bean : fileTypeBeans) {
      initializeMatchers(bean.getPluginDescriptor(), bean);
    }

    for (FileTypeBean bean : fileTypeBeans) {
      if (bean.implementationClass == null) continue;

      if (myPendingFileTypes.containsKey(bean.name)) {
        handleFileTypesConflict(bean, myPendingFileTypes.get(bean.name));
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

  private static void handleFileTypesConflict(FileTypeBean bean, FileTypeBean otherBean) {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      PluginConflictReporter conflictReporter = application.getService(PluginConflictReporter.class);
      if (conflictReporter != null) {
        Set<PluginId> conflictingPlugins = new HashSet<>();
        if (bean.getPluginId() != null) {
          conflictingPlugins.add(bean.getPluginId());
        }
        if (otherBean.getPluginId() != null) {
          conflictingPlugins.add(otherBean.getPluginId());
        }
        boolean hasConflictWithPlatform = bean.getPluginId() == null || otherBean.getPluginId() == null;
        conflictReporter.reportConflict(conflictingPlugins, hasConflictWithPlatform);
        return;
      }
    }

    LOG.error(new PluginException("Trying to override already registered file type '" + bean.name + "'", bean.getPluginId()));
  }

  private static void initializeMatchers(@NotNull Object context, @NotNull FileTypeBean bean) {
    bean.addMatchers(parseExtensions(context, Strings.notNullize(bean.extensions)));
    bean.addMatchers(parse(context, Strings.notNullize(bean.fileNames), token -> new ExactFileNameMatcher(token)));
    bean.addMatchers(parse(context, Strings.notNullize(bean.fileNamesCaseInsensitive), token -> new ExactFileNameMatcher(token, true)));
    bean.addMatchers(parse(context, Strings.notNullize(bean.patterns), token -> FileNameMatcherFactory.getInstance().createMatcher(token)));
  }

  private void instantiatePendingFileTypes() {
    Collection<FileTypeBean> fileTypes = withReadLock(() -> new ArrayList<>(myPendingFileTypes.values()));
    for (FileTypeBean fileTypeBean : fileTypes) {
      mergeOrInstantiateFileTypeBean(fileTypeBean);
    }
  }

  @NotNull
  private FileType mergeOrInstantiateFileTypeBean(@NotNull FileTypeBean fileTypeBean) {
    StandardFileType type = withReadLock(() -> myStandardFileTypes.get(fileTypeBean.name));
    if (type == null) {
      return getFileTypeOrUnknown(instantiateFileTypeBean(fileTypeBean));
    }
    type.matchers.addAll(fileTypeBean.getMatchers());
    for (FileNameMatcher matcher : fileTypeBean.getMatchers()) {
      myPatternsTable.addAssociation(matcher, descriptorForStandard(type));
    }
    return type.fileType;
  }

  private FileTypeWithDescriptor instantiateFileTypeBean(@NotNull FileTypeBean bean) {
    Lock writeLock = myPendingInitializationLock.writeLock();
    writeLock.lock();
    try {
      FileType fileType;
      String fileTypeName = bean.name;
      if (!myPendingFileTypes.containsKey(fileTypeName)) {
        FileTypeWithDescriptor ftd = mySchemeManager.findSchemeByName(fileTypeName);
        if (ftd != null && !(ftd.fileType instanceof AbstractFileType)) {
          return ftd;
        }
      }

      PluginId pluginId = bean.getPluginDescriptor().getPluginId();
      try {
        if (bean.fieldName == null) {
          // uncached - cached by FileTypeManagerImpl and not by bean
          fileType = ApplicationManager.getApplication().instantiateClass(bean.implementationClass, bean.getPluginDescriptor());
        }
        else {
          Field field = ApplicationManager.getApplication().loadClass(bean.implementationClass, bean.getPluginDescriptor()).getDeclaredField(bean.fieldName);
          field.setAccessible(true);
          fileType = (FileType)field.get(null);
        }
      }
      catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
        LOG.error(new PluginException(e, pluginId));
        return null;
      }

      if (!fileType.getName().equals(fileTypeName)) {
        LOG.error(new PluginException("Incorrect name specified in <fileType>, should be " + fileType.getName() + ", actual " + fileTypeName,
                                      pluginId));
      }
      if (fileType instanceof LanguageFileType) {
        LanguageFileType languageFileType = (LanguageFileType)fileType;
        String expectedLanguage = languageFileType.isSecondary() ? null : languageFileType.getLanguage().getID();
        if (!Objects.equals(bean.language, expectedLanguage)) {
          LOG.error(new PluginException("Incorrect language specified in <fileType> for " + fileType.getName() +
                                        ", should be " + expectedLanguage + ", actual " + bean.language, pluginId));
        }
      }

      StandardFileType standardFileType = new StandardFileType(fileType, bean.getPluginDescriptor(), bean.getMatchers());
      myStandardFileTypes.put(bean.name, standardFileType);
      registerFileTypeWithoutNotification(fileType, bean.getPluginDescriptor(), standardFileType.matchers, true);

      if (bean.hashBangs != null) {
        for (String hashBang : StringUtil.split(bean.hashBangs, ";")) {
          myPatternsTable.addHashBangPattern(hashBang, new FileTypeWithDescriptor(fileType, bean.getPluginDescriptor()));
          myInitialAssociations.addHashBangPattern(hashBang, fileType);
        }
      }

      PluginAdvertiserExtensionsStateService pluginAdvertiser = PluginAdvertiserExtensionsStateService.getInstance();
      for (FileNameMatcher matcher : standardFileType.matchers) {
        pluginAdvertiser.registerLocalPlugin(matcher, bean.getPluginDescriptor());
      }

      myPendingAssociations.removeAllAssociations(bean);
      myPendingFileTypes.remove(fileTypeName);

      return new FileTypeWithDescriptor(fileType, bean.getPluginDescriptor());
    }
    finally {
      writeLock.unlock();
    }
  }

  @TestOnly
  boolean toLog;
  boolean toLog() {
    return toLog;
  }
  void log(@NonNls String message) {
    LOG.debug(message + " - " + Thread.currentThread());
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
    instantiatePendingFileTypeByName(name);
    StandardFileType stdFileType = withReadLock(() -> myStandardFileTypes.get(name));
    return stdFileType != null ? stdFileType.fileType : PlainTextFileType.INSTANCE;
  }

  private void instantiatePendingFileTypeByName(@NonNls @NotNull String name) {
    FileTypeBean bean = withReadLock(() -> myPendingFileTypes.get(name));
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
    for (FileTypeWithDescriptor ftd : mySchemeManager.loadSchemes()) {
      isAtLeastOneStandardFileTypeHasBeenRead |= myInitialAssociations.hasAssociationsFor(ftd.fileType);
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
      return getFileTypeOrUnknown(instantiateFileTypeBean(pendingFileType));
    }
    FileType type = withReadLock(() -> {
      FileTypeWithDescriptor ftd = myPatternsTable.findAssociatedFileType(fileName);
      return ftd==null?null:ftd.fileType;
    });
    return ObjectUtils.notNull(type, UnknownFileType.INSTANCE);
  }

  @Override
  public void freezeFileTypeTemporarilyIn(@NotNull VirtualFile file, @NotNull Runnable runnable) {
    FileType fileType = file.isDirectory() ? null : file.getFileType();
    Pair<VirtualFile, FileType> old = FILE_TYPE_FIXED_TEMPORARILY.get();
    FILE_TYPE_FIXED_TEMPORARILY.set(new Pair<>(file, fileType));
    if (toLog()) {
      log("F: freezeFileTypeTemporarilyIn(" + file.getName() + ") to " + fileType +" in "+Thread.currentThread());
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
    if (file instanceof StubVirtualFile) {
      if (fileType == null && content == null && file instanceof FakeVirtualFile) {
        if (ScratchUtil.isScratch(file.getParent())) return PlainTextFileType.INSTANCE;
      }
    }
    else if (fileType == null) {
      return myDetectionService.getOrDetectFromContent(file, content);
    }
    return ObjectUtils.notNull(fileType, UnknownFileType.INSTANCE);
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

    for (FileTypeIdentifiableByVirtualFile specialType : mySpecialFileTypes) {
      if (specialType.isMyFileType(file)) {
        if (toLog()) {
          log("getByFile(" + file.getName() + "): Special file type: " + specialType.getName());
        }
        return specialType;
      }
    }

    FileType fileType = getFileTypeByFileName(file.getNameSequence());
    if (fileType == UnknownFileType.INSTANCE || fileType == DetectedByContentFileType.INSTANCE) {
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
    for (FileTypeWithDescriptor ftd: getAllFileTypeWithDescriptors()) {
      if (fileTypeName.equals(ftd.fileType.getName())) {
        return ftd.fileType;
      }
    }
    return null;
  }

  @NotNull
  private List<FileTypeWithDescriptor> getAllFileTypeWithDescriptors() {
    return mySchemeManager.getAllSchemes();
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
      return (LanguageFileType)instantiateFileTypeBean(bean).fileType;
    }

    // Do not use getRegisteredFileTypes(), to avoid instantiating all pending file types
    return withReadLock(() -> language.findMyFileType(ContainerUtil.map2Array(getAllFileTypeWithDescriptors(), FileType.EMPTY_ARRAY, ftd->ftd.fileType)));
  }

  @Override
  @NotNull
  public FileType getFileTypeByExtension(@NotNull String extension) {
    return getFileTypeWithDescriptorByExtension(extension).fileType;
  }

  @NotNull
  FileTypeWithDescriptor getFileTypeWithDescriptorByExtension(@NotNull String extension) {
    FileTypeBean pendingFileType = withReadLock(() -> myPendingAssociations.findByExtension(extension));
    if (pendingFileType != null) {
      FileTypeWithDescriptor type = instantiateFileTypeBean(pendingFileType);
      return type == null ? coreDescriptorFor(UnknownFileType.INSTANCE) : type;
    }
    FileTypeWithDescriptor ftd = withReadLock(() -> myPatternsTable.findByExtension(extension));
    return ftd == null ? coreDescriptorFor(UnknownFileType.INSTANCE) : ftd;
  }

  @Override
  @Deprecated
  public void registerFileType(@NotNull FileType fileType) {
    registerFileType(fileType, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  @Override
  public void registerFileType(@NotNull FileType type, @NotNull List<? extends FileNameMatcher> defaultAssociations) {
    DeprecatedMethodException.report("Use fileType extension instead.");
    ApplicationManager.getApplication().runWriteAction(() -> {
      fireBeforeFileTypesChanged();
      registerFileTypeWithoutNotification(type, detectPluginDescriptor(type).pluginDescriptor, defaultAssociations, true);
      fireFileTypesChanged(type, null);
    });
  }

  @Override
  public void unregisterFileType(@NotNull FileType fileType) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      fireBeforeFileTypesChanged();
      unregisterFileTypeWithoutNotification(fileType);
      myStandardFileTypes.remove(fileType.getName());
      if (fileType instanceof LanguageFileType) {
        Language language = ((LanguageFileType)fileType).getLanguage();
        if (fileType.getClass().getClassLoader().equals(language.getClass().getClassLoader())) {
          Language.unregisterLanguage(language);
        }
      }
      fireFileTypesChanged(null, fileType);
    });
  }

  private void unregisterFileTypeWithoutNotification(@NotNull FileType fileType) {
    PluginDescriptor pluginDescriptor = findPluginDescriptor(fileType);
    FileTypeWithDescriptor ftd = FileTypeWithDescriptor.allFor(fileType);
    List<FileNameMatcher> matchers = myPatternsTable.getAssociations(ftd);
    // delete all records "extension xxx is removed from standard type X because the new fileType grabbed it to itself"
    getRemovedMappingTracker().removeIf(mapping -> mapping.getFileTypeName().equals(fileType.getName())
                                                   || matchers.contains(mapping.getFileNameMatcher()));
    myPatternsTable.removeAllAssociations(ftd);
    myInitialAssociations.removeAllAssociations(fileType);
    mySchemeManager.removeScheme(fileType.getName());
    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      FileTypeIdentifiableByVirtualFile fakeFileType = (FileTypeIdentifiableByVirtualFile)fileType;
      mySpecialFileTypes = ArrayUtil.remove(mySpecialFileTypes, fakeFileType, FileTypeIdentifiableByVirtualFile.ARRAY_FACTORY);
    }
    if (pluginDescriptor != null) {
      removeFromDuplicates(fileType, pluginDescriptor);
    }
  }

  @Override
  public FileType @NotNull [] getRegisteredFileTypes() {
    instantiatePendingFileTypes();
    return ContainerUtil.map2Array(getAllFileTypeWithDescriptors(), FileType.class, ftd->ftd.fileType);
  }

  @NotNull
  List<FileTypeWithDescriptor> getRegisteredFileTypeWithDescriptors() {
    instantiatePendingFileTypes();
    return getAllFileTypeWithDescriptors();
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
    return masks.isEmpty() ? "" : String.join(";", masks) + ";";
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
    Set<String> tempSet = CollectionFactory.createSmallMemoryFootprintSet();
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

    return withReadLock(() -> myPatternsTable.getAssociatedExtensions(FileTypeWithDescriptor.allFor(type)));
  }

  @Override
  @NotNull
  public List<FileNameMatcher> getAssociations(@NotNull FileType type) {
    instantiatePendingFileTypeByName(type.getName());

    return withReadLock(() -> myPatternsTable.getAssociations(FileTypeWithDescriptor.allFor(type)));
  }

  @Override
  public void associate(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
    associate(detectPluginDescriptor(type), matcher, true);
  }

  @NotNull
  static FileTypeWithDescriptor detectPluginDescriptor(@NotNull FileType type) {
    PluginDescriptor pluginDescriptor = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(type.getClass().getName());
    if (pluginDescriptor == null) pluginDescriptor = coreIdeaPluginDescriptor();
    return new FileTypeWithDescriptor(type, pluginDescriptor);
  }

  private static @NotNull FileType getFileTypeOrUnknown(@Nullable FileTypeWithDescriptor ftd) {
    return ftd != null ? ftd.fileType : UnknownFileType.INSTANCE;
  }

  @Override
  public void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
    removeAssociation(detectPluginDescriptor(type), matcher, true);
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
    CachedFileType.clearCache();
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).fileTypesChanged(new FileTypeEvent(this, addedFileType, removedFileType));
  }

  private final Map<FileTypeListener, MessageBusConnection> myAdapters = new ConcurrentHashMap<>();
  @Override
  public void addFileTypeListener(@NotNull FileTypeListener listener) {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(TOPIC, listener);
    myAdapters.put(listener, connection);
  }

  @Override
  public void removeFileTypeListener(@NotNull FileTypeListener listener) {
    MessageBusConnection connection = myAdapters.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }

  @Override
  public void loadState(@NotNull Element state) {
    int savedVersion = StringUtilRt.parseInt(state.getAttributeValue(ATTRIBUTE_VERSION), 0);

    for (Element element : state.getChildren()) {
      if (ELEMENT_IGNORE_FILES.equals(element.getName())) {
        myIgnoredPatterns.setIgnoreMasks(Strings.notNullize(element.getAttributeValue(ATTRIBUTE_LIST)));
      }
      else if (ELEMENT_EXTENSION_MAP.equals(element.getName())) {
        readGlobalMappings(element, false);
      }
    }

    migrateFromOldVersion(savedVersion);

    myIgnoredFileCache.clearCache();

    myDetectionService.loadState(state);
  }

  private void migrateFromOldVersion(int savedVersion) {
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

    if (savedVersion < 18) {
      // we want .hprof back, we can open it in our profiler
      unignoreMask("*.hprof");
    }
  }

  private void unignoreMask(@NonNls @NotNull String maskToRemove) {
    Set<String> masks = new LinkedHashSet<>(myIgnoredPatterns.getIgnoreMasks());
    masks.remove(maskToRemove);

    myIgnoredPatterns.clearPatterns();
    for (String each : masks) {
      myIgnoredPatterns.addIgnoreMask(each);
    }
  }

  private void readGlobalMappings(@NotNull Element e, boolean isAddToInit) {
    myRemovedMappingTracker.load(e);

    for (Pair<FileNameMatcher, String> association : AbstractFileType.readAssociations(e)) {
      String fileTypeName = association.getSecond();
      FileNameMatcher matcher = association.getFirst();
      FileTypeWithDescriptor ftd = getFileTypeWithDescriptorByName(fileTypeName);
      FileType type = ftd==null?null:ftd.fileType;
      FileTypeBean pendingFileTypeBean = myPendingAssociations.findAssociatedFileType(matcher);
      if (pendingFileTypeBean != null) {
        instantiateFileTypeBean(pendingFileTypeBean);
      }

      if (type == null) {
        myUnresolvedMappings.put(matcher, fileTypeName);
      }
      else {
        if (PlainTextFileType.INSTANCE.equals(type)) {
          FileTypeWithDescriptor textFtd = myPatternsTable.findAssociatedFileType(matcher);
          FileType newFileType = textFtd==null?null:textFtd.fileType;
          if (newFileType != null && newFileType != PlainTextFileType.INSTANCE && newFileType != UnknownFileType.INSTANCE) {
            myRemovedMappingTracker.add(matcher, newFileType.getName(), false);
          }
        }
        associate(ftd, matcher, false);
        if (isAddToInit) {
          myInitialAssociations.addAssociation(matcher, type);
        }
      }
    }

    for (Map.Entry<String, FileTypeWithDescriptor> entry : readHashBangs(e).entrySet()) {
      String hashBang = entry.getKey();
      FileTypeWithDescriptor ftd = entry.getValue();
      myPatternsTable.addHashBangPattern(hashBang, ftd);
      if (isAddToInit) {
        myInitialAssociations.addHashBangPattern(hashBang, ftd.fileType);
      }
    }

    for (RemovedMappingTracker.RemovedMapping mapping : myRemovedMappingTracker.getRemovedMappings()) {
      FileTypeWithDescriptor ftd = getFileTypeWithDescriptorByName(mapping.getFileTypeName());
      if (ftd != null) {
        removeAssociation(ftd, mapping.getFileNameMatcher(), false);
      }
    }
  }

  @NotNull
  private Map<String, FileTypeWithDescriptor> readHashBangs(@NotNull Element e) {
    List<Element> children = e.getChildren("hashBang");
    Map<String, FileTypeWithDescriptor> result = CollectionFactory.createSmallMemoryFootprintMap(children.size());
    for (Element hashBangTag : children) {
      String typeName = hashBangTag.getAttributeValue("type");
      String hashBangPattern = hashBangTag.getAttributeValue("value");
      FileTypeWithDescriptor ftd = typeName == null ? null : getFileTypeWithDescriptorByName(typeName);
      if (hashBangPattern == null || ftd == null) continue;
      result.put(hashBangPattern, ftd);
    }
    return result;
  }

  private void addIgnore(@NonNls @NotNull String ignoreMask) {
    myIgnoredPatterns.addIgnoreMask(ignoreMask);
  }

  private void restoreStandardFileExtensions() {
    for (String name : FILE_TYPES_WITH_PREDEFINED_EXTENSIONS) {
      StandardFileType stdFileType = myStandardFileTypes.get(name);
      if (stdFileType != null) {
        FileType fileType = stdFileType.fileType;
        for (FileNameMatcher matcher : myPatternsTable.getAssociations(FileTypeWithDescriptor.allFor(fileType))) {
          FileType defaultFileType = myInitialAssociations.findAssociatedFileType(matcher);
          if (defaultFileType != null && defaultFileType != fileType) {
            removeAssociation(coreDescriptorFor(fileType), matcher, false);
            associate(coreDescriptorFor(defaultFileType), matcher, false);
          }
        }

        for (FileNameMatcher matcher : myInitialAssociations.getAssociations(fileType)) {
          associate(coreDescriptorFor(fileType), matcher, false);
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
      ignoreFiles = String.join(";", strings) + ";";
    }

    if (!ignoreFiles.equalsIgnoreCase(DEFAULT_IGNORED)) {
      // empty means empty list - we need to distinguish null and empty to apply or not to apply default value
      state.addContent(new Element(ELEMENT_IGNORE_FILES).setAttribute(ATTRIBUTE_LIST, ignoreFiles));
    }

    Element extensionMap = new Element(ELEMENT_EXTENSION_MAP);

    List<FileTypeWithDescriptor> notExternalizableFileTypes = new ArrayList<>();
    for (FileTypeWithDescriptor ftd: getAllFileTypeWithDescriptors()) {
      if (!(ftd.fileType instanceof AbstractFileType) || myDefaultTypes.contains(ftd)) {
        notExternalizableFileTypes.add(ftd);
      }
    }
    if (!notExternalizableFileTypes.isEmpty()) {
      notExternalizableFileTypes.sort(Comparator.comparing(ftd -> ftd.fileType.getName()));
      for (FileTypeWithDescriptor ftd : notExternalizableFileTypes) {
        writeExtensionsMap(extensionMap, ftd, true);
      }
    }

    // https://youtrack.jetbrains.com/issue/IDEA-138366
    myRemovedMappingTracker.save(extensionMap);

    if (!myUnresolvedMappings.isEmpty()) {
      List<Map.Entry<FileNameMatcher, String>> entries = new ArrayList<>(myUnresolvedMappings.entrySet());
      entries.sort(Comparator.comparing(e->e.getKey().getPresentableString()));

      for (Map.Entry<FileNameMatcher, String> entry : entries) {
        FileNameMatcher fileNameMatcher = entry.getKey();
        String typeName = entry.getValue();
        Element content = AbstractFileType.writeMapping(typeName, fileNameMatcher, true);
        if (content != null) {
          extensionMap.addContent(content);
        }
      }
    }

    if (!extensionMap.getChildren().isEmpty()) {
      state.addContent(extensionMap);
    }

    if (!state.getChildren().isEmpty()) {
      state.setAttribute(ATTRIBUTE_VERSION, String.valueOf(VERSION));
    }
    return state;
  }

  private void writeExtensionsMap(@NotNull Element extensionMap, @NotNull FileTypeWithDescriptor ftd, boolean specifyTypeName) {
    FileType type = ftd.fileType;
    List<FileNameMatcher> associations = myPatternsTable.getAssociations(ftd);
    Set<FileNameMatcher> defaultAssociations = new HashSet<>(myInitialAssociations.getAssociations(type));

    for (FileNameMatcher matcher : associations) {
      boolean isDefaultAssociationContains = defaultAssociations.remove(matcher);
      if (!isDefaultAssociationContains && shouldSave(type)) {
        Element content = AbstractFileType.writeMapping(type.getName(), matcher, specifyTypeName);
        if (content != null) {
          extensionMap.addContent(content);
        }
      }
    }
    List<String> readOnlyHashBangs = myInitialAssociations.getHashBangPatterns(type);
    List<String> hashBangPatterns = myPatternsTable.getHashBangPatterns(ftd);
    hashBangPatterns.sort(Comparator.naturalOrder());
    for (String hashBangPattern : hashBangPatterns) {
      if (!readOnlyHashBangs.contains(hashBangPattern)) {
        Element hashBangTag = new Element("hashBang");
        hashBangTag.setAttribute("value", hashBangPattern);
        hashBangTag.setAttribute("type", type.getName());
        extensionMap.addContent(hashBangTag);
      }
    }
    List<FileNameMatcher> removedMappings = myRemovedMappingTracker.getMappingsForFileType(type.getName());
    // do not store removed mappings which are going to be stored anyway via RemovedMappingTracker.save()
    defaultAssociations.removeIf(matcher -> removedMappings.contains(matcher));
    myRemovedMappingTracker.saveRemovedMappingsForFileType(extensionMap, type.getName(), defaultAssociations, specifyTypeName);
  }

  private FileTypeWithDescriptor getFileTypeWithDescriptorByName(@NotNull String name) {
    instantiatePendingFileTypeByName(name);
    return withReadLock(() -> mySchemeManager.findSchemeByName(name));
  }

  @NotNull
  private static List<FileNameMatcher> parseExtensions(@NotNull Object context, @NotNull String semicolonDelimitedExtensions) {
    return parse(context, semicolonDelimitedExtensions, ext -> new ExtensionFileNameMatcher(ext));
  }

  @NotNull
  private static List<FileNameMatcher> parse(@NotNull Object context,
                                             @NotNull String semicolonDelimitedTokens,
                                             @NotNull Function<? super String, ? extends FileNameMatcher> matcherFactory) {
    if (semicolonDelimitedTokens.isEmpty()) {
      return Collections.emptyList();
    }
    StringTokenizer tokenizer = new StringTokenizer(semicolonDelimitedTokens, FileTypeConsumer.EXTENSION_DELIMITER, false);
    List<FileNameMatcher> list = new ArrayList<>(StringUtil.countChars(semicolonDelimitedTokens, ';')+1);
    while (tokenizer.hasMoreTokens()) {
      String ext = tokenizer.nextToken().trim();
      if (Strings.isEmpty(ext)) {
        throw new InvalidDataException("Token must not be empty but got: '"+semicolonDelimitedTokens+"' in "+context);
      }
      list.add(matcherFactory.apply(ext));
    }
    return list;
  }

  /**
   * Registers a standard file type. Doesn't notifyListeners any change events.
   * returns list of shown conflict notifications.
   */
  @NotNull
  private List<ConflictingFileTypeMappingTracker.ResolveConflictResult> registerFileTypeWithoutNotification(@NotNull FileType newFileType,
                                                                                                            @NotNull PluginDescriptor newPluginDescriptor,
                                                                                                            @NotNull List<? extends FileNameMatcher> matchers,
                                                                                                            boolean addScheme) {
    List<ConflictingFileTypeMappingTracker.ResolveConflictResult> notificationsShown = new ArrayList<>();
    FileTypeWithDescriptor newFtd = new FileTypeWithDescriptor(newFileType, newPluginDescriptor);
    if (addScheme) {
      FileTypeWithDescriptor oldFileType = mySchemeManager.findSchemeByName(newFileType.getName());
      if (oldFileType != null && !(oldFileType.fileType instanceof AbstractFileType)) {
        throw new IllegalArgumentException(newFileType + " already registered");
      }
      mySchemeManager.addScheme(newFtd);
      myPendingFileTypes.remove(newFileType.getName());
      myPendingAssociations.removeAllAssociations(bean -> bean.name.equals(newFileType.getName()));
      checkFileTypeNamesUniqueness(newFtd);
    }
    List<FileNameMatcher> removedMappings = myRemovedMappingTracker.getMappingsForFileType(newFileType.getName());
    for (FileNameMatcher matcher : matchers) {
      if (removedMappings.contains(matcher)) {
        continue;
      }

      FileTypeWithDescriptor oldFtd = myPatternsTable.findAssociatedFileType(matcher);
      FileType oldFileType = oldFtd==null?null:oldFtd.fileType;
      ConflictingFileTypeMappingTracker.ResolveConflictResult result = myConflictingMappingTracker.warnAndResolveConflict(matcher, oldFtd, newFtd);
      if (!result.approved) {
        notificationsShown.add(result);
      }
      FileTypeWithDescriptor resolvedFtd = result.resolved;
      if (!resolvedFtd.equals(oldFtd)) {
        myPatternsTable.addAssociation(matcher, resolvedFtd);
        if (result.approved && oldFileType != null) {
          myRemovedMappingTracker.add(matcher, oldFileType.getName(), true);
        }
      }
      else if (oldFileType instanceof AbstractFileType && result.approved) {
        myPatternsTable.addAssociation(matcher, newFtd);
      }

      myInitialAssociations.addAssociation(matcher, newFileType);
    }

    if (newFileType instanceof FileTypeIdentifiableByVirtualFile) {
      mySpecialFileTypes = ArrayUtil.append(mySpecialFileTypes, (FileTypeIdentifiableByVirtualFile)newFileType, FileTypeIdentifiableByVirtualFile.ARRAY_FACTORY);
    }

    return notificationsShown;
  }

  private void checkFileTypeNamesUniqueness(@NotNull FileTypeWithDescriptor newFtd) {
    checkUnique(newFtd, names.computeIfAbsent(newFtd.pluginDescriptor, __->new ConcurrentHashMap<>()), "getName", FileType::getName);
    checkUnique(newFtd, displayNames.computeIfAbsent(newFtd.pluginDescriptor, __->new ConcurrentHashMap<>()), "getDisplayName", FileType::getDisplayName);
    checkUnique(newFtd, descriptions.computeIfAbsent(newFtd.pluginDescriptor, __->new ConcurrentHashMap<>()), "getDescription", FileType::getDescription);
  }

  // check that method "getter" returns unique strings across all file types
  private static void checkUnique(@NotNull FileTypeWithDescriptor newFtd,
                                  @NotNull Map<? super String, FileTypeWithDescriptor> names,
                                  @NotNull String getterName,
                                  @NotNull Function<? super FileType, String> nameExtractor) {
    FileType newFileType = newFtd.fileType;
    String name = nameExtractor.apply(newFileType);
    FileTypeWithDescriptor prevFtd = names.put(name, newFtd);
    if (prevFtd != null
        // should be able to override AbstractFileType silently
        && (prevFtd.fileType instanceof AbstractFileType) == (newFileType instanceof AbstractFileType)) {
      String error = "\n" + prevFtd + " (" + prevFtd.fileType.getClass() + ") and" +
                     "\n" + newFtd + " (" + newFileType.getClass() + ")\n" +
                     " both have the same ." + getterName + "(): '" + name + "'. " +
                     "Please override either one's " + getterName + "() to something unique.";
      PluginDescriptor pluginToBlame = prevFtd.pluginDescriptor.isBundled() ? newFtd.pluginDescriptor : prevFtd.pluginDescriptor;
      if (prevFtd.pluginDescriptor.isBundled() || newFtd.pluginDescriptor.isBundled()) {
        // file type from the plugin conflicts with bundled file type
        LOG.error(new PluginException(error, pluginToBlame.getPluginId()));
      }
      else {
        // two plugins conflict between themselves (or one plugin has multiple personality disordered file types), we wash our hands in this case
        LOG.warn(new PluginException(error, pluginToBlame.getPluginId()));
      }
    }
  }

  // maps pluginId -> duplicates for this plugin
  private final Map<PluginDescriptor, Map<String, FileTypeWithDescriptor>> names = new ConcurrentHashMap<>();
  private final Map<PluginDescriptor, Map<String, FileTypeWithDescriptor>> descriptions = new ConcurrentHashMap<>();
  private final Map<PluginDescriptor, Map<String, FileTypeWithDescriptor>> displayNames = new ConcurrentHashMap<>();

  private void bindUnresolvedMappings(@NotNull FileType fileType) {
    for (FileNameMatcher matcher : new ArrayList<>(myUnresolvedMappings.keySet())) {
      String name = myUnresolvedMappings.get(matcher);
      if (fileType.getName().equals(name)) {
        myPatternsTable.addAssociation(matcher, coreDescriptorFor(fileType));
        myUnresolvedMappings.remove(matcher);
      }
    }

    for (FileNameMatcher matcher : myRemovedMappingTracker.getMappingsForFileType(fileType.getName())) {
      removeAssociation(FileTypeWithDescriptor.allFor(fileType), matcher, false);
    }
  }

  @NotNull
  static FileTypeManagerImpl.FileTypeWithDescriptor coreDescriptorFor(@NotNull FileType fileType) {
    return new FileTypeWithDescriptor(fileType, coreIdeaPluginDescriptor());
  }
  @NotNull
  private static FileTypeManagerImpl.FileTypeWithDescriptor descriptorForStandard(@NotNull StandardFileType stdType) {
    return new FileTypeWithDescriptor(stdType.fileType, stdType.pluginDescriptor);
  }

  @NotNull
  private FileType loadFileType(@NotNull Object context,
                                @NotNull Element typeElement,
                                @NotNull PluginDescriptor pluginDescriptor,
                                boolean isDefault) {
    String fileTypeName = typeElement.getAttributeValue(ATTRIBUTE_NAME);

    String extensionsStr = Objects.requireNonNullElse(typeElement.getAttributeValue("extensions"), "");
    if (isDefault && !extensionsStr.isEmpty()) {
      // todo support wildcards
      extensionsStr = filterAlreadyRegisteredExtensions(extensionsStr);
    }

    FileTypeWithDescriptor ftd = isDefault ? getFileTypeWithDescriptorByName(fileTypeName) : null;
    if (ftd != null) {
      return ftd.fileType;
    }

    Element element = typeElement.getChild(AbstractFileType.ELEMENT_HIGHLIGHTING);
    FileType type;
    if (element == null) {
      type = UserBinaryFileType.INSTANCE;
    }
    else {
      SyntaxTable table = AbstractFileType.readSyntaxTable(element);
      type = new AbstractFileType(table);
      ((AbstractFileType)type).initSupport();
    }

    @NlsSafe String fileTypeDescr = typeElement.getAttributeValue(ATTRIBUTE_DESCRIPTION);
    String iconPath = typeElement.getAttributeValue("icon");
    setFileTypeAttributes((UserFileType<?>)type, fileTypeName, fileTypeDescr, iconPath);
    registerFileTypeWithoutNotification(type, pluginDescriptor, parseExtensions(context, extensionsStr), isDefault);

    if (isDefault) {
      myDefaultTypes.add(new FileTypeWithDescriptor(type, pluginDescriptor));
      if (type instanceof ExternalizableFileType) {
        ((ExternalizableFileType)type).markDefaultSettings();
      }
    }
    else {
      Element extensions = typeElement.getChild(ELEMENT_EXTENSION_MAP);
      if (extensions != null) {
        FileTypeWithDescriptor newftd = new FileTypeWithDescriptor(type, pluginDescriptor);
        for (Pair<FileNameMatcher, String> association : AbstractFileType.readAssociations(extensions)) {
          associate(newftd, association.getFirst(), false);
        }

        for (RemovedMappingTracker.RemovedMapping removedAssociation : RemovedMappingTracker.readRemovedMappings(extensions)) {
          removeAssociation(newftd, removedAssociation.getFileNameMatcher(), false);
        }
      }
    }
    return type;
  }

  @NotNull
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
    return builder == null ? "" : builder.toString();
  }

  private static void setFileTypeAttributes(@NotNull UserFileType<?> fileType,
                                            @Nullable String name,
                                            @Nullable @NlsContexts.Label String description,
                                            @Nullable String iconPath) {
    if (!Strings.isEmptyOrSpaces(iconPath)) {
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

  @NotNull
  FileTypeAssocTable<FileTypeWithDescriptor> getExtensionMap() {
    instantiatePendingFileTypes();

    return myPatternsTable;
  }

  void setPatternsTable(@NotNull Set<? extends FileTypeWithDescriptor> fileTypes, @NotNull FileTypeAssocTable<FileTypeWithDescriptor> assocTable) {
    Map<FileNameMatcher, FileTypeWithDescriptor> removedMappings = getExtensionMap().getRemovedMappings(assocTable, fileTypes);
    fireBeforeFileTypesChanged();
    for (FileTypeWithDescriptor existing : getRegisteredFileTypeWithDescriptors()) {
      if (!fileTypes.contains(existing)) {
        mySchemeManager.removeScheme(existing);
      }
    }
    for (FileTypeWithDescriptor ftd : fileTypes) {
      mySchemeManager.addScheme(ftd);
      if (ftd.fileType instanceof AbstractFileType) {
        ((AbstractFileType)ftd.fileType).initSupport();
      }
    }
    myPatternsTable = assocTable.copy();
    fireFileTypesChanged();

    myRemovedMappingTracker.removeIf(mapping -> {
      String fileTypeName = mapping.getFileTypeName();
      FileTypeWithDescriptor fileType = getFileTypeWithDescriptorByName(fileTypeName);
      FileNameMatcher matcher = mapping.getFileNameMatcher();
      return fileType != null && assocTable.isAssociatedWith(fileType, matcher);
    });
    for (Map.Entry<FileNameMatcher, FileTypeWithDescriptor> entry : removedMappings.entrySet()) {
      myRemovedMappingTracker.add(entry.getKey(), entry.getValue().fileType.getName(), true);
    }
  }

  void associate(@NotNull FileTypeWithDescriptor ftd, @NotNull FileNameMatcher matcher, boolean fireChange) {
    FileType fileType = ftd.fileType;
    // delete "this matcher is removed from this file type" record
    myRemovedMappingTracker.removeIf(mapping -> matcher.equals(mapping.getFileNameMatcher()) && fileType.getName().equals(mapping.getFileTypeName()));
    if (!myPatternsTable.isAssociatedWith(ftd, matcher)) {
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myPatternsTable.addAssociation(matcher, ftd);
      if (fireChange) {
        fireFileTypesChanged();
      }
    }
  }

  private void removeAssociation(@NotNull FileTypeWithDescriptor ftd, @NotNull FileNameMatcher matcher, boolean fireChange) {
    if (myPatternsTable.isAssociatedWith(ftd, matcher)) {
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myPatternsTable.removeAssociation(matcher, ftd);
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

  private <T, E extends Throwable> T withReadLock(@NotNull ThrowableComputable<T, E> computable) throws E {
    return ConcurrencyUtil.withLock(myPendingInitializationLock.readLock(), computable);
  }

  @NotNull
  RemovedMappingTracker getRemovedMappingTracker() {
    return myRemovedMappingTracker;
  }

  public PluginDescriptor findPluginDescriptor(@NotNull FileType fileType) {
    for (FileTypeWithDescriptor ftd : getAllFileTypeWithDescriptors()) {
      if (ftd.fileType.equals(fileType)) {
        return ftd.pluginDescriptor;
      }
    }
    return null;
  }

  private class PluginFileTypeConsumer implements FileTypeConsumer {
    private final PluginDescriptor myPluginDescriptor;

    PluginFileTypeConsumer(@NotNull PluginDescriptor pluginDescriptor) {
      myPluginDescriptor = pluginDescriptor;
    }

    @Override
    public void consume(@NotNull FileType fileType) {
      register(fileType, myPluginDescriptor, parseExtensions("filetypes.xml/" + fileType.getName(), fileType.getDefaultExtension()));
    }

    @Override
    public void consume(@NotNull FileType fileType, @NotNull String semicolonDelimitedExtensions) {
      register(fileType, myPluginDescriptor, parseExtensions("filetypes.xml/" + fileType.getName(), semicolonDelimitedExtensions));
    }

    @Override
    public void consume(@NotNull FileType fileType, FileNameMatcher @NotNull ... matchers) {
      register(fileType, myPluginDescriptor, Arrays.asList(matchers));
    }

    @Override
    public FileType getStandardFileTypeByName(@NotNull String name) {
      StandardFileType type = myStandardFileTypes.get(name);
      return type != null ? type.fileType : null;
    }

    private void register(@NotNull FileType fileType,
                          @NotNull PluginDescriptor pluginDescriptor,
                          @NotNull List<? extends FileNameMatcher> fileNameMatchers) {
      String typeName = fileType.getName();
      instantiatePendingFileTypeByName(typeName);
      for (FileNameMatcher matcher : fileNameMatchers) {
        FileTypeBean pendingTypeByMatcher = myPendingAssociations.findAssociatedFileType(matcher);
        if (pendingTypeByMatcher != null) {
          PluginId pluginId = pendingTypeByMatcher.getPluginId();
          if (pluginId == null || PluginManagerCore.CORE_ID.equals(pluginId)) {
            instantiateFileTypeBean(pendingTypeByMatcher);
          }
        }
      }

      StandardFileType type = myStandardFileTypes.get(typeName);
      if (type == null) {
        myStandardFileTypes.put(typeName, new StandardFileType(fileType, pluginDescriptor, fileNameMatchers));
      }
      else {
        type.matchers.addAll(fileNameMatchers);
      }
    }
  }
}
