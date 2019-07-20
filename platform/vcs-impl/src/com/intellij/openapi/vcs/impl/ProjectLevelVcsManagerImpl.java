// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListenerImpl;
import com.intellij.openapi.vcs.checkout.CompositeCheckoutListener;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.history.VcsHistoryCache;
import com.intellij.openapi.vcs.impl.projectlevelman.*;
import com.intellij.openapi.vcs.roots.VcsRootScanner;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vcs.update.UpdatedFilesListener;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.project.ProjectKt;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.ViewUpdateInfoNotification;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@State(name = "ProjectLevelVcsManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ProjectLevelVcsManagerImpl extends ProjectLevelVcsManagerEx implements ProjectComponent, PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl");
  @NonNls private static final String SETTINGS_EDITED_MANUALLY = "settingsEditedManually";

  private final ProjectLevelVcsManagerSerialization mySerialization;
  private final OptionsAndConfirmations myOptionsAndConfirmations;

  private final NewMappings myMappings;
  private final Project myProject;
  private final ToolWindowManager myToolWindowManager;
  private final MappingsToRoots myMappingsToRoots;

  private ConsoleView myConsole;

  @Nullable private final VcsInitialization myInitialization;

  @NonNls private static final String ELEMENT_MAPPING = "mapping";
  @NonNls private static final String ATTRIBUTE_DIRECTORY = "directory";
  @NonNls private static final String ATTRIBUTE_VCS = "vcs";
  @NonNls private static final String ATTRIBUTE_DEFAULT_PROJECT = "defaultProject";
  @NonNls private static final String ELEMENT_ROOT_SETTINGS = "rootSettings";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";

  private boolean myMappingsLoaded;
  private boolean myHaveLegacyVcsConfiguration;

  @NotNull private final AtomicInteger myBackgroundOperationCounter = new AtomicInteger();

  private final Set<ActionKey> myBackgroundRunningTasks = new HashSet<>();

  private final List<Pair<String, ConsoleViewContentType>> myPendingOutput = new ArrayList<>();

  private final VcsHistoryCache myVcsHistoryCache;
  private final ContentRevisionCache myContentRevisionCache;
  private final FileIndexFacade myExcludedIndex;
  private final FileTypeManager myFileTypeManager;
  private final VcsAnnotationLocalChangesListenerImpl myAnnotationLocalChangesListener;

  public ProjectLevelVcsManagerImpl(Project project,
                                    FileStatusManager manager,
                                    FileIndexFacade excludedFileIndex,
                                    ProjectManager projectManager,
                                    FileTypeManager fileTypeManager,
                                    DefaultVcsRootPolicy defaultVcsRootPolicy) {
    myProject = project;
    mySerialization = new ProjectLevelVcsManagerSerialization();
    myOptionsAndConfirmations = new OptionsAndConfirmations();

    if (project.isDefault()) {
      myInitialization = null;
      myToolWindowManager = null;
    }
    else {
      // there is no ToolWindowManager for default project, can't pass it via parameter
      myToolWindowManager = ToolWindowManager.getInstance(project);
      myInitialization = new VcsInitialization(myProject);
      Disposer.register(project, myInitialization); // wait for the thread spawned in VcsInitialization to terminate
      projectManager.addProjectManagerListener(project, new ProjectManagerListener() {
        @Override
        public void projectClosing(@NotNull Project project) {
          Disposer.dispose(myInitialization);
        }
      });
    }

    myMappings = new NewMappings(myProject, this, manager, defaultVcsRootPolicy);
    myMappingsToRoots = new MappingsToRoots(myMappings, myProject);

    myVcsHistoryCache = new VcsHistoryCache();
    myContentRevisionCache = new ContentRevisionCache();
    VcsListener vcsListener = () -> myVcsHistoryCache.clearHistory();
    myExcludedIndex = excludedFileIndex;
    myFileTypeManager = fileTypeManager;
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, vcsListener);
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN, vcsListener);
    connection.subscribe(UpdatedFilesListener.UPDATED_FILES, myContentRevisionCache::clearCurrent);
    myAnnotationLocalChangesListener = new VcsAnnotationLocalChangesListenerImpl(myProject, this);
  }

  public void registerVcs(AbstractVcs vcs) {
    AllVcses.getInstance(myProject).registerManually(vcs);
  }

  @Override
  @Nullable
  public AbstractVcs findVcsByName(String name) {
    if (name == null) return null;
    AbstractVcs result = myProject.isDisposed() ? null : AllVcses.getInstance(myProject).getByName(name);
    ProgressManager.checkCanceled();
    return result;
  }

  @Override
  @Nullable
  public VcsDescriptor getDescriptor(final String name) {
    if (name == null) return null;
    if (myProject.isDisposed()) return null;
    return AllVcses.getInstance(myProject).getDescriptor(name);
  }

  @Override
  public void iterateVfUnderVcsRoot(VirtualFile file, Processor<? super VirtualFile> processor) {
    VcsRootIterator.iterateVfUnderVcsRoot(myProject, file, processor);
  }

  @Override
  public VcsDescriptor[] getAllVcss() {
    return AllVcses.getInstance(myProject).getAll();
  }

  public boolean haveVcses() {
    return !AllVcses.getInstance(myProject).isEmpty();
  }

  @Override
  public void dispose() {
    releaseConsole();
    Disposer.dispose(myMappings);
    Disposer.dispose(myAnnotationLocalChangesListener);

    if (myToolWindowManager != null && myToolWindowManager.getToolWindow(ToolWindowId.VCS) != null) {
      myToolWindowManager.unregisterToolWindow(ToolWindowId.VCS);
    }
  }

  @NotNull
  @Override
  public VcsAnnotationLocalChangesListener getAnnotationLocalChangesListener() {
    return myAnnotationLocalChangesListener;
  }

  @Override
  public void projectOpened() {
    addInitializationRequest(VcsInitObject.AFTER_COMMON, () -> {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        List<VcsRootChecker> checkers = VcsRootChecker.EXTENSION_POINT_NAME.getExtensionList();
        if (checkers.size() != 0) {
          VcsRootScanner.start(myProject, checkers);
        }
      }
    });
  }

  @Override
  public void projectClosed() {
    releaseConsole();
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "ProjectLevelVcsManager";
  }

  @Override
  public boolean checkAllFilesAreUnder(AbstractVcs abstractVcs, VirtualFile[] files) {
    if (files == null) return false;
    for (VirtualFile file : files) {
      if (getVcsFor(file) != abstractVcs) {
        return false;
      }
    }
    return true;
  }

  @Override
  @Nullable
  public AbstractVcs getVcsFor(@NotNull VirtualFile file) {
    if (myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.vcs : null;
  }

  @Override
  @Nullable
  public AbstractVcs getVcsFor(@NotNull FilePath file) {
    if (myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.vcs : null;
  }

  @Override
  @Nullable
  public VirtualFile getVcsRootFor(@Nullable VirtualFile file) {
    if (file == null || myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.root : null;
  }

  @Override
  @Nullable
  public VcsRoot getVcsRootObjectFor(@Nullable VirtualFile file) {
    if (file == null || myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? new VcsRoot(root.vcs, root.root) : null;
  }

  @Override
  @Nullable
  public VirtualFile getVcsRootFor(@Nullable FilePath file) {
    if (file == null || myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.root : null;
  }

  @Override
  public VcsRoot getVcsRootObjectFor(@Nullable FilePath file) {
    if (file == null || myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? new VcsRoot(root.vcs, root.root) : null;
  }

  public void unregisterVcs(@NotNull AbstractVcs vcs) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && myMappings.haveActiveVcs(vcs.getName())) {
      // unlikely
      LOG.warn("Active vcs '" + vcs.getName() + "' is being unregistered. Remove from mappings first.");
    }
    myMappings.beingUnregistered(vcs.getName());
    AllVcses.getInstance(myProject).unregisterManually(vcs);
  }

  @Nullable
  @Override
  public ContentManager getContentManager() {
    ToolWindow changes = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.VCS);
    return changes == null ? null : changes.getContentManager();
  }

  @Override
  public boolean checkVcsIsActive(AbstractVcs vcs) {
    return checkVcsIsActive(vcs.getName());
  }

  @Override
  public boolean checkVcsIsActive(final String vcsName) {
    return myMappings.haveActiveVcs(vcsName);
  }

  @Override
  public AbstractVcs[] getAllActiveVcss() {
    return myMappings.getActiveVcses();
  }

  @Override
  public boolean hasActiveVcss() {
    return myMappings.hasActiveVcss();
  }

  @Override
  public boolean hasAnyMappings() {
    return !myMappings.isEmpty();
  }

  @Deprecated
  @Override
  public void addMessageToConsoleWindow(final String message, final TextAttributes attributes) {
    addMessageToConsoleWindow(message, new ConsoleViewContentType("", attributes));
  }

  @Override
  public void addMessageToConsoleWindow(@Nullable final String message, @NotNull final ConsoleViewContentType contentType) {
    if (!Registry.is("vcs.showConsole")) {
      return;
    }
    if (StringUtil.isEmptyOrSpaces(message)) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      // for default and disposed projects the ContentManager is not available.
      if (myProject.isDisposed() || myProject.isDefault()) return;
      final ContentManager contentManager = getContentManager();
      if (contentManager == null) {
        myPendingOutput.add(Pair.create(message, contentType));
      }
      else {
        getOrCreateConsoleContent(contentManager);
        printToConsole(message, contentType);
      }
    }, ModalityState.defaultModalityState());
  }

  private void getOrCreateConsoleContent(final ContentManager contentManager) {
    final String displayName = VcsBundle.message("vcs.console.toolwindow.display.name");
    Content content = contentManager.findContent(displayName);
    if (content == null) {
      releaseConsole();

      ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(myProject).getConsole();
      myConsole = console;

      SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
      panel.setContent(console.getComponent());

      ActionToolbar toolbar = ActionManager.getInstance()
        .createActionToolbar("VcsManager", new DefaultActionGroup(console.createConsoleActions()), false);
      panel.setToolbar(toolbar.getComponent());

      content = ContentFactory.SERVICE.getInstance().createContent(panel, displayName, true);
      content.setDisposer(() -> releaseConsole());
      content.setPreferredFocusedComponent(() -> console.getPreferredFocusableComponent());
      contentManager.addContent(content);

      for (Pair<String, ConsoleViewContentType> pair : myPendingOutput) {
        printToConsole(pair.first, pair.second);
      }
      myPendingOutput.clear();
    }
  }

  private void printToConsole(@NotNull String message, @NotNull ConsoleViewContentType contentType) {
    myConsole.print(message + "\n", contentType);
  }

  private void releaseConsole() {
    if (myConsole != null) {
      Disposer.dispose(myConsole);
      myConsole = null;
    }
  }

  @Override
  @NotNull
  public VcsShowSettingOption getOptions(VcsConfiguration.StandardOption option) {
    return myOptionsAndConfirmations.getOptions(option);
  }

  @Override
  public List<VcsShowOptionsSettingImpl> getAllOptions() {
    return myOptionsAndConfirmations.getAllOptions();
  }

  @Override
  @NotNull
  public VcsShowSettingOption getStandardOption(@NotNull VcsConfiguration.StandardOption option, @NotNull AbstractVcs vcs) {
    final VcsShowOptionsSettingImpl options = (VcsShowOptionsSettingImpl)getOptions(option);
    options.addApplicableVcs(vcs);
    return options;
  }

  @Override
  @NotNull
  public VcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName, @NotNull AbstractVcs vcs) {
    return myOptionsAndConfirmations.getOrCreateCustomOption(vcsActionName, vcs);
  }

  @CalledInAwt
  @Override
  public void showProjectOperationInfo(final UpdatedFiles updatedFiles, String displayActionName) {
    UpdateInfoTree tree = showUpdateProjectInfo(updatedFiles, displayActionName, ActionInfo.STATUS, false);
    if (tree != null) ViewUpdateInfoNotification.focusUpdateInfoTree(myProject, tree);
  }

  @CalledInAwt
  @Nullable
  @Override
  public UpdateInfoTree showUpdateProjectInfo(UpdatedFiles updatedFiles, String displayActionName, ActionInfo actionInfo, boolean canceled) {
    if (!myProject.isOpen() || myProject.isDisposed()) return null;
    ContentManager contentManager = getContentManager();
    if (contentManager == null) {
      return null;  // content manager is made null during dispose; flag is set later
    }
    final UpdateInfoTree updateInfoTree = new UpdateInfoTree(contentManager, myProject, updatedFiles, displayActionName, actionInfo);
    ContentUtilEx.addTabbedContent(contentManager, updateInfoTree, "Update Info", DateFormatUtil.formatDateTime(System.currentTimeMillis()), false, updateInfoTree);
    updateInfoTree.expandRootChildren();
    return updateInfoTree;
  }

  @Override
  public List<VcsDirectoryMapping> getDirectoryMappings() {
    return myMappings.getDirectoryMappings();
  }

  @Override
  public List<VcsDirectoryMapping> getDirectoryMappings(final AbstractVcs vcs) {
    return myMappings.getDirectoryMappings(vcs.getName());
  }

  @Override
  @Nullable
  public VcsDirectoryMapping getDirectoryMappingFor(@Nullable FilePath file) {
    if (file == null || myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.mapping : null;
  }

  @Nullable
  private VcsDirectoryMapping getDirectoryMappingFor(@Nullable VirtualFile file) {
    if (file == null || myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.mapping : null;
  }

  @Override
  @Deprecated
  public void setDirectoryMapping(@NotNull String path, @Nullable String activeVcsName) {
    if (myMappingsLoaded) return;            // ignore per-module VCS settings if the mapping table was loaded from .ipr
    myHaveLegacyVcsConfiguration = true;
    myMappings.setMapping(FileUtil.toSystemIndependentName(path), activeVcsName);
  }

  @Deprecated
  public void setAutoDirectoryMapping(@NotNull String path, @Nullable String activeVcsName) {
    setAutoDirectoryMappings(ContainerUtil.append(myMappings.getDirectoryMappings(), new VcsDirectoryMapping(path, activeVcsName)));
  }

  public void setAutoDirectoryMappings(@NotNull List<? extends VcsDirectoryMapping> mappings) {
    myMappings.setDirectoryMappings(mappings);
    myMappings.cleanupMappings();
  }

  public void removeDirectoryMapping(@NotNull VcsDirectoryMapping mapping) {
    myMappings.removeDirectoryMapping(mapping);
  }

  @Override
  public void setDirectoryMappings(@NotNull List<VcsDirectoryMapping> items) {
    myHaveLegacyVcsConfiguration = true;
    myMappings.setDirectoryMappings(items);
  }

  @Override
  public void scheduleMappedRootsUpdate() {
    myMappings.scheduleMappedRootsUpdate();
  }

  @Override
  public void iterateVcsRoot(final VirtualFile root, final Processor<? super FilePath> iterator) {
    VcsRootIterator.iterateVcsRoot(myProject, root, iterator);
  }

  @Override
  public void iterateVcsRoot(VirtualFile root,
                             Processor<? super FilePath> iterator,
                             @Nullable VirtualFileFilter directoryFilter) {
    VcsRootIterator.iterateVcsRoot(myProject, root, iterator, directoryFilter);
  }

  @Nullable
  @Override
  public Element getState() {
    Element element = new Element("state");
    mySerialization.writeExternalUtil(element, myOptionsAndConfirmations);
    if (myHaveLegacyVcsConfiguration) {
      element.setAttribute(SETTINGS_EDITED_MANUALLY, "true");
    }
    return element;
  }

  @Override
  public void loadState(@NotNull Element state) {
    mySerialization.readExternalUtil(state, myOptionsAndConfirmations);
    final Attribute attribute = state.getAttribute(SETTINGS_EDITED_MANUALLY);
    if (attribute != null) {
      try {
        myHaveLegacyVcsConfiguration = attribute.getBooleanValue();
      }
      catch (DataConversionException ignored) {
      }
    }
  }

  @Override
  @NotNull
  public VcsShowConfirmationOption getStandardConfirmation(@NotNull VcsConfiguration.StandardConfirmation option,
                                                           AbstractVcs vcs) {
    final VcsShowConfirmationOptionImpl result = getConfirmation(option);
    if (vcs != null) {
      result.addApplicableVcs(vcs);
    }
    return result;
  }

  @Override
  public List<VcsShowConfirmationOptionImpl> getAllConfirmations() {
    return myOptionsAndConfirmations.getAllConfirmations();
  }

  @Override
  @NotNull
  public VcsShowConfirmationOptionImpl getConfirmation(VcsConfiguration.StandardConfirmation option) {
    return myOptionsAndConfirmations.getConfirmation(option);
  }

  private final Map<VcsListener, MessageBusConnection> myAdapters = new HashMap<>();

  @Override
  public void addVcsListener(VcsListener listener) {
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(VCS_CONFIGURATION_CHANGED, listener);
    myAdapters.put(listener, connection);
  }

  @Override
  public void removeVcsListener(VcsListener listener) {
    final MessageBusConnection connection = myAdapters.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }

  @Override
  public void startBackgroundVcsOperation() {
    myBackgroundOperationCounter.incrementAndGet();
  }

  @Override
  public void stopBackgroundVcsOperation() {
    // in fact, the condition is "should not be called under ApplicationManager.invokeLater() and similar"
    assert !ApplicationManager.getApplication().isDispatchThread() || ApplicationManager.getApplication().isUnitTestMode();
    int counter = myBackgroundOperationCounter.getAndDecrement();
    LOG.assertTrue(counter > 0, "myBackgroundOperationCounter was " + counter + " while should have been > 0");
  }

  @Override
  public boolean isBackgroundVcsOperationRunning() {
    return myBackgroundOperationCounter.get() > 0;
  }

  @Override
  public List<VirtualFile> getRootsUnderVcsWithoutFiltering(final AbstractVcs vcs) {
    return myMappings.getMappingsAsFilesUnderVcs(vcs);
  }

  @Override
  @NotNull
  public VirtualFile[] getRootsUnderVcs(@NotNull AbstractVcs vcs) {
    return myMappingsToRoots.getRootsUnderVcs(vcs);
  }

  @Override
  public List<VirtualFile> getDetailedVcsMappings(final AbstractVcs vcs) {
    return myMappingsToRoots.getDetailedVcsMappings(vcs);
  }

  @Override
  public VirtualFile[] getAllVersionedRoots() {
    List<VirtualFile> vFiles = new ArrayList<>();
    final AbstractVcs[] vcses = myMappings.getActiveVcses();
    for (AbstractVcs vcs : vcses) {
      Collections.addAll(vFiles, getRootsUnderVcs(vcs));
    }
    return VfsUtilCore.toVirtualFileArray(vFiles);
  }

  @Override
  @NotNull
  public VcsRoot[] getAllVcsRoots() {
    List<VcsRoot> vcsRoots = new ArrayList<>();
    final AbstractVcs[] vcses = myMappings.getActiveVcses();
    for (AbstractVcs vcs : vcses) {
      final VirtualFile[] roots = getRootsUnderVcs(vcs);
      for (VirtualFile root : roots) {
        vcsRoots.add(new VcsRoot(vcs, root));
      }
    }
    return vcsRoots.toArray(new VcsRoot[0]);
  }

  @Override
  public void notifyDirectoryMappingChanged() {
    BackgroundTaskUtil.syncPublisher(myProject, VCS_CONFIGURATION_CHANGED).directoryMappingChanged();
  }

  void readDirectoryMappings(final Element element) {
    final List<VcsDirectoryMapping> mappingsList = new ArrayList<>();
    boolean haveNonEmptyMappings = false;
    for (Element child : element.getChildren(ELEMENT_MAPPING)) {
      String vcs = child.getAttributeValue(ATTRIBUTE_VCS);
      String directory = child.getAttributeValue(ATTRIBUTE_DIRECTORY);
      if (directory == null) continue;

      VcsRootSettings rootSettings = null;
      Element rootSettingsElement = child.getChild(ELEMENT_ROOT_SETTINGS);
      if (rootSettingsElement != null) {
        String className = rootSettingsElement.getAttributeValue(ATTRIBUTE_CLASS);
        AbstractVcs vcsInstance = findVcsByName(vcs);
        if (vcsInstance != null && className != null) {
          rootSettings = vcsInstance.createEmptyVcsRootSettings();
          if (rootSettings != null) {
            try {
              rootSettings.readExternal(rootSettingsElement);
            }
            catch (InvalidDataException e) {
              LOG.error("Failed to load VCS root settings class " + className + " for VCS " + vcsInstance.getClass().getName(), e);
            }
          }
        }
      }

      VcsDirectoryMapping mapping = new VcsDirectoryMapping(directory, vcs, rootSettings);
      mappingsList.add(mapping);

      haveNonEmptyMappings |= !mapping.isDefaultMapping();
    }
    boolean defaultProject = Boolean.TRUE.toString().equals(element.getAttributeValue(ATTRIBUTE_DEFAULT_PROJECT));
    // run autodetection if there's no VCS in default project and
    if (haveNonEmptyMappings || !defaultProject) {
      myMappingsLoaded = true;
    }
    myMappings.setDirectoryMappings(mappingsList);
  }

  void writeDirectoryMappings(@NotNull Element element) {
    if (myProject.isDefault()) {
      element.setAttribute(ATTRIBUTE_DEFAULT_PROJECT, Boolean.TRUE.toString());
    }
    for (VcsDirectoryMapping mapping : getDirectoryMappings()) {
      VcsRootSettings rootSettings = mapping.getRootSettings();
      if (rootSettings == null && StringUtil.isEmpty(mapping.getDirectory()) && StringUtil.isEmpty(mapping.getVcs())) {
        continue;
      }

      Element child = new Element(ELEMENT_MAPPING);
      child.setAttribute(ATTRIBUTE_DIRECTORY, mapping.getDirectory());
      child.setAttribute(ATTRIBUTE_VCS, mapping.getVcs());
      if (rootSettings != null) {
        Element rootSettingsElement = new Element(ELEMENT_ROOT_SETTINGS);
        rootSettingsElement.setAttribute(ATTRIBUTE_CLASS, rootSettings.getClass().getName());
        try {
          rootSettings.writeExternal(rootSettingsElement);
          child.addContent(rootSettingsElement);
        }
        catch (WriteExternalException e) {
          // don't add element
        }
      }
      element.addContent(child);
    }
  }

  public boolean needAutodetectMappings() {
    return !myHaveLegacyVcsConfiguration && !myMappingsLoaded;
  }

  /**
   * Used to guess VCS for automatic mapping through a look into a working copy
   */
  @Override
  @Nullable
  public AbstractVcs findVersioningVcs(VirtualFile file) {
    final VcsDescriptor[] vcsDescriptors = getAllVcss();
    VcsDescriptor probableVcs = null;
    for (VcsDescriptor vcsDescriptor : vcsDescriptors) {
      if (vcsDescriptor.probablyUnderVcs(file)) {
        if (probableVcs != null) {
          return null;
        }
        probableVcs = vcsDescriptor;
      }
    }
    return probableVcs == null ? null : findVcsByName(probableVcs.getName());
  }


  @NotNull
  @Override
  public VcsRootChecker getRootChecker(@NotNull AbstractVcs vcs) {
    for (VcsRootChecker checker : VcsRootChecker.EXTENSION_POINT_NAME.getExtensionList()) {
      if (checker.getSupportedVcs().equals(vcs.getKeyInstanceMethod())) return checker;
    }
    return new DefaultVcsRootChecker(vcs);
  }

  @Override
  public CheckoutProvider.Listener getCompositeCheckoutListener() {
    return new CompositeCheckoutListener(myProject);
  }

  @Override
  public void fireDirectoryMappingsChanged() {
    if (myProject.isOpen() && !myProject.isDisposed()) {
      myMappings.mappingsChanged();
    }
  }

  /**
   * @return VCS name for default mapping, if any
   */
  @Nullable
  @Override
  public String haveDefaultMapping() {
    return myMappings.haveDefaultMapping();
  }

  /**
   * @deprecated {@link BackgroundableActionLock}
   */
  @Deprecated
  public BackgroundableActionEnabledHandler getBackgroundableActionHandler(final VcsBackgroundableActions action) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return new BackgroundableActionEnabledHandler(myProject, action);
  }

  @CalledInAwt
  boolean isBackgroundTaskRunning(@NotNull Object... keys) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myBackgroundRunningTasks.contains(new ActionKey(keys));
  }

  @CalledInAwt
  void startBackgroundTask(@NotNull Object... keys) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(myBackgroundRunningTasks.add(new ActionKey(keys)));
  }

  @CalledInAwt
  void stopBackgroundTask(@NotNull Object... keys) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(myBackgroundRunningTasks.remove(new ActionKey(keys)));
  }

  public void addInitializationRequest(final VcsInitObject vcsInitObject, final Runnable runnable) {
    if (myInitialization != null) {
      ApplicationManager.getApplication().runReadAction(() -> myInitialization.add(vcsInitObject, runnable));
    }
  }

  @Override
  public boolean isFileInContent(@Nullable final VirtualFile vf) {
    if (vf == null) return false;
    return ReadAction.compute(() -> {
      boolean isUnderProject = isFileInBaseDir(vf) ||
                               isInDirectoryBasedRoot(vf) ||
                               hasExplicitMapping(vf) ||
                               myExcludedIndex.isInContent(vf) ||
                               !Registry.is("ide.hide.excluded.files") && myExcludedIndex.isExcludedFile(vf);
      return isUnderProject && !isIgnored(vf);
    });
  }

  @Override
  public boolean isIgnored(@NotNull VirtualFile vf) {
    return ReadAction.compute(() -> {
      if (myProject.isDisposed()) return false;

      if (Registry.is("ide.hide.excluded.files")) {
        return myExcludedIndex.isExcludedFile(vf);
      }
      else {
        return myExcludedIndex.isUnderIgnored(vf);
      }
    });
  }

  @Override
  public boolean isIgnored(@NotNull FilePath filePath) {
    return ReadAction.compute(() -> {
      if (myProject.isDisposed()) return false;

      if (Registry.is("ide.hide.excluded.files")) {
        VirtualFile vf = ChangesUtil.findValidParentAccurately(filePath);
        return vf != null && myExcludedIndex.isExcludedFile(vf);
      }
      else {
        for (String name : StringUtil.tokenize(filePath.getPath(), "/")) {
          if (myFileTypeManager.isFileIgnored(name)) return true;
        }
        return false;
      }
    });
  }

  private boolean isInDirectoryBasedRoot(@NotNull VirtualFile file) {
    if (ProjectKt.isDirectoryBased(myProject)) {
      return ProjectKt.getStateStore(myProject).isProjectFile(file);
    }
    return false;
  }

  private boolean isFileInBaseDir(@NotNull VirtualFile file) {
    VirtualFile baseDir = myProject.getBaseDir();
    if (baseDir == null) return false;

    if (file.isDirectory()) {
      return baseDir.equals(file);
    }
    else {
      return baseDir.equals(file.getParent());
    }
  }

  private boolean hasExplicitMapping(@NotNull VirtualFile vFile) {
    final VcsDirectoryMapping mapping = getDirectoryMappingFor(vFile);
    return mapping != null && !mapping.isDefaultMapping();
  }

  @Override
  public VcsHistoryCache getVcsHistoryCache() {
    return myVcsHistoryCache;
  }

  @Override
  public ContentRevisionCache getContentRevisionCache() {
    return myContentRevisionCache;
  }

  @TestOnly
  public void waitForInitialized() {
    if (myInitialization != null) {
      myInitialization.waitFinished();
    }
  }

  private static class ActionKey {
    private final Object[] myObjects;

    ActionKey(@NotNull Object... objects) {
      myObjects = objects;
    }

    @Override
    public final boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      return Arrays.equals(myObjects, ((ActionKey)o).myObjects);
    }

    @Override
    public final int hashCode() {
      return Arrays.hashCode(myObjects);
    }

    @Override
    public String toString() {
      return getClass() + " - " + Arrays.toString(myObjects);
    }
  }
}
