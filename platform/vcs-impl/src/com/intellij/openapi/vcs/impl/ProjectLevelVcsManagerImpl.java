// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.trustedProjects.TrustedProjectsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.checkout.CompositeCheckoutListener;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.history.VcsHistoryCache;
import com.intellij.openapi.vcs.impl.projectlevelman.*;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.project.ProjectKt;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.ViewUpdateInfoNotification;
import com.intellij.vcs.console.VcsConsoleTabService;
import com.intellij.vcsUtil.VcsImplUtil;
import kotlin.Pair;
import kotlinx.coroutines.CoroutineScope;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@State(name = "VcsDirectoryMappings", storages = @Storage("vcs.xml"))
public final class ProjectLevelVcsManagerImpl extends ProjectLevelVcsManagerEx implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(ProjectLevelVcsManagerImpl.class);

  private final NewMappings myMappings;
  private final Project myProject;

  @NonNls private static final String ELEMENT_MAPPING = "mapping";
  @NonNls private static final String ATTRIBUTE_DIRECTORY = "directory";
  @NonNls private static final String ATTRIBUTE_VCS = "vcs";
  @NonNls private static final String ATTRIBUTE_DEFAULT_PROJECT = "defaultProject";
  @NonNls private static final String ELEMENT_ROOT_SETTINGS = "rootSettings";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";

  private boolean myMappingsLoaded;

  private final @NotNull AtomicInteger myBackgroundOperationCounter = new AtomicInteger();

  private final Set<ActionKey> myBackgroundRunningTasks = ConcurrentCollectionFactory.createConcurrentSet();

  public ProjectLevelVcsManagerImpl(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    myProject = project;

    myMappings = new NewMappings(myProject, this, coroutineScope);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myMappings);
  }

  public static ProjectLevelVcsManagerImpl getInstanceImpl(@NotNull Project project) {
    return (ProjectLevelVcsManagerImpl)getInstance(project);
  }

  @TestOnly
  public void registerVcs(AbstractVcs vcs) {
    AllVcses.getInstance(myProject).registerManually(vcs);
  }

  @Override
  public @Nullable AbstractVcs findVcsByName(@Nullable String name) {
    if (name == null) return null;
    AbstractVcs vcs = AllVcses.getInstance(myProject).getByName(name);
    if (vcs == null && myProject.isDisposed()) {
      // Take readLock to avoid race between Project.isDisposed and Disposer.dispose.
      ReadAction.run(ProgressManager::checkCanceled);
    }
    return vcs;
  }

  @Override
  public @Nullable VcsDescriptor getDescriptor(final String name) {
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

  @Override
  public AbstractVcs @NotNull [] getAllSupportedVcss() {
    return AllVcses.getInstance(myProject).getSupportedVcses();
  }

  public boolean haveVcses() {
    return !AllVcses.getInstance(myProject).isEmpty();
  }

  @Override
  public @NotNull VcsAnnotationLocalChangesListener getAnnotationLocalChangesListener() {
    return myProject.getService(VcsAnnotationLocalChangesListener.class);
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
  public @NotNull @NlsSafe String getShortNameForVcsRoot(@NotNull VirtualFile root) {
    String shortName = myMappings.getShortNameFor(root);
    if (shortName != null) return shortName;

    VirtualFile projectDir = myProject.getBaseDir();
    String relativePath = projectDir != null ? VfsUtilCore.getRelativePath(root, projectDir, File.separatorChar) : null;
    if (relativePath != null) {
      return relativePath.isEmpty() ? root.getName() : relativePath;
    }

    return root.getPresentableUrl();
  }

  @Override
  public @Nullable AbstractVcs getVcsFor(@Nullable VirtualFile file) {
    if (myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.vcs : null;
  }

  @Override
  public @Nullable AbstractVcs getVcsFor(@Nullable FilePath file) {
    if (myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.vcs : null;
  }

  @Override
  public @Nullable VirtualFile getVcsRootFor(@Nullable VirtualFile file) {
    if (file == null || myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.root : null;
  }

  @Override
  public @Nullable VcsRoot getVcsRootObjectFor(@Nullable VirtualFile file) {
    if (file == null || myProject.isDisposed()) {
      return null;
    }

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? new VcsRoot(root.vcs, root.root) : null;
  }

  @Override
  public @Nullable VirtualFile getVcsRootFor(@Nullable FilePath file) {
    if (file == null || myProject.isDisposed()) {
      return null;
    }

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.root : null;
  }

  @Override
  public VcsRoot getVcsRootObjectFor(@Nullable FilePath file) {
    if (file == null || myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? new VcsRoot(root.vcs, root.root) : null;
  }

  @ApiStatus.Internal
  public @NotNull List<VcsRoot> getVcsRootObjectsForDefaultMapping() {
    List<NewMappings.MappedRoot> detectedRoots = ContainerUtil.filter(myMappings.getAllMappedRoots(), root -> {
      AbstractVcs vcs = root.vcs;
      return root.mapping.isDefaultMapping() &&
             vcs != null && vcs.getCustomConvertor() == null;
    });
    return ContainerUtil.map(detectedRoots, root -> new VcsRoot(root.vcs, root.root));
  }

  @TestOnly
  public void unregisterVcs(@NotNull AbstractVcs vcs) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && myMappings.haveActiveVcs(vcs.getName())) {
      // unlikely
      LOG.warn("Active vcs '" + vcs.getName() + "' is being unregistered. Remove from mappings first.");
    }
    myMappings.beingUnregistered(vcs.getName());
    AllVcses.getInstance(myProject).unregisterManually(vcs);
  }

  @Override
  public @Nullable ContentManager getContentManager() {
    ToolWindow changes = ToolWindowManager.getInstance(myProject).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    return changes == null ? null : changes.getContentManager();
  }

  @Override
  public boolean checkVcsIsActive(@NotNull AbstractVcs vcs) {
    return checkVcsIsActive(vcs.getName());
  }

  @Override
  public boolean checkVcsIsActive(final String vcsName) {
    return myMappings.haveActiveVcs(vcsName);
  }

  @Override
  public AbstractVcs @NotNull [] getAllActiveVcss() {
    return myMappings.getActiveVcses();
  }

  @Override
  public @Nullable AbstractVcs getSingleVCS() {
    AbstractVcs[] vcses = getAllActiveVcss();
    return vcses.length == 1 ? vcses[0] : null;
  }

  @Override
  public boolean hasActiveVcss() {
    return myMappings.hasActiveVcss();
  }

  @Override
  public boolean areVcsesActivated() {
    return myMappings.isActivated();
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
  public void addMessageToConsoleWindow(@Nullable String message, @NotNull ConsoleViewContentType contentType) {
    VcsConsoleTabService.getInstance(myProject).addMessage(message, contentType);
  }

  @Override
  public void addMessageToConsoleWindow(@Nullable VcsConsoleLine line) {
    VcsConsoleTabService.getInstance(myProject).addMessage(line);
  }


  @RequiresEdt
  @Override
  public void showProjectOperationInfo(final UpdatedFiles updatedFiles, String displayActionName) {
    UpdateInfoTree tree = showUpdateProjectInfo(updatedFiles, displayActionName, ActionInfo.STATUS, false);
    if (tree != null) ViewUpdateInfoNotification.focusUpdateInfoTree(myProject, tree);
  }

  @RequiresEdt
  @Override
  public @Nullable UpdateInfoTree showUpdateProjectInfo(UpdatedFiles updatedFiles,
                                                        String displayActionName,
                                                        ActionInfo actionInfo,
                                                        boolean canceled) {
    if (!myProject.isOpen() || myProject.isDisposed()) return null;
    ContentManager contentManager = getContentManager();
    if (contentManager == null) {
      return null;  // content manager is made null during dispose; flag is set later
    }
    final UpdateInfoTree updateInfoTree = new UpdateInfoTree(contentManager, myProject, updatedFiles, displayActionName, actionInfo);
    String tabName = DateFormatUtil.formatDateTime(System.currentTimeMillis());
    ContentUtilEx.addTabbedContent(contentManager, updateInfoTree, "Update Info",
                                   VcsBundle.messagePointer("vcs.update.tab.name"), () -> tabName,
                                   false, updateInfoTree);
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
  public @Nullable VcsDirectoryMapping getDirectoryMappingFor(@Nullable FilePath file) {
    if (file == null || myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.mapping : null;
  }

  private @Nullable VcsDirectoryMapping getDirectoryMappingFor(@Nullable VirtualFile file) {
    if (file == null || myProject.isDisposed()) return null;

    NewMappings.MappedRoot root = myMappings.getMappedRootFor(file);
    return root != null ? root.mapping : null;
  }

  @Override
  public void setDirectoryMapping(@NotNull String path, @Nullable String activeVcsName) {
    if (myMappingsLoaded) {
      // ignore per-module VCS settings if the mapping table was loaded from .ipr
      return;
    }

    OptionsAndConfirmationsHolder.getInstance(myProject).markHasVcsConfiguration();
    myMappings.setMapping(FileUtil.toSystemIndependentName(path), activeVcsName);
  }

  public void registerNewDirectMappings(Collection<Pair<VirtualFile, AbstractVcs>> detectedRoots) {
    var mappings = new ArrayList<>(myMappings.getDirectoryMappings());
    var knownMappedRoots = mappings.stream().map(VcsDirectoryMapping::getDirectory).collect(Collectors.toSet());
    var newMappings = detectedRoots.stream()
      .map(pair -> new VcsDirectoryMapping(pair.component1().getPath(), pair.component2().getName()))
      .filter(it -> !knownMappedRoots.contains(it.getDirectory())).toList();
    mappings.addAll(newMappings);
    setAutoDirectoryMappings(mappings);
  }

  public void setAutoDirectoryMappings(@NotNull List<VcsDirectoryMapping> mappings) {
    myMappings.setDirectoryMappings(mappings);
    myMappings.cleanupMappings();
  }

  public void removeDirectoryMapping(@NotNull VcsDirectoryMapping mapping) {
    myMappings.removeDirectoryMapping(mapping);
  }

  @Override
  public void setDirectoryMappings(@NotNull List<VcsDirectoryMapping> items) {
    OptionsAndConfirmationsHolder.getInstance(myProject).markHasVcsConfiguration();
    myMappings.setDirectoryMappings(items);
  }

  @Override
  public void scheduleMappedRootsUpdate() {
    myMappings.scheduleMappedRootsUpdate();
  }

  public void updateMappedVcsesImmediately() {
    myMappings.updateMappedVcsesImmediately();
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

  @Override
  public @NotNull VcsShowSettingOption getStandardOption(@NotNull VcsConfiguration.StandardOption option, @NotNull AbstractVcs vcs) {
    final PersistentVcsShowSettingOption options = getOptions(option);
    options.addApplicableVcs(vcs);
    return options;
  }

  @Override
  public @NotNull VcsShowConfirmationOption getStandardConfirmation(@NotNull VcsConfiguration.StandardConfirmation option,
                                                                    AbstractVcs vcs) {
    final PersistentVcsShowConfirmationOption result = getConfirmation(option);
    if (vcs != null) {
      result.addApplicableVcs(vcs);
    }
    return result;
  }

  @Override
  public @NotNull List<PersistentVcsShowConfirmationOption> getAllConfirmations() {
    return getOptionsAndConfirmations().getAllConfirmations();
  }

  @Override
  public @NotNull PersistentVcsShowConfirmationOption getConfirmation(VcsConfiguration.StandardConfirmation option) {
    return getOptionsAndConfirmations().getConfirmation(option);
  }

  @Override
  public @NotNull PersistentVcsShowSettingOption getOptions(VcsConfiguration.StandardOption option) {
    return getOptionsAndConfirmations().getOption(option);
  }

  @Override
  public @NotNull List<PersistentVcsShowSettingOption> getAllOptions() {
    return getOptionsAndConfirmations().getAllOptions();
  }

  private @NotNull OptionsAndConfirmations getOptionsAndConfirmations() {
    return OptionsAndConfirmationsHolder.getInstance(myProject).getOptionsAndConfirmations();
  }

  @Override
  public void startBackgroundVcsOperation() {
    myBackgroundOperationCounter.incrementAndGet();
  }

  @Override
  public void stopBackgroundVcsOperation() {
    // in fact, the condition is "should not be called under ApplicationManager.invokeLater() and similar"
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    int counter = myBackgroundOperationCounter.getAndDecrement();
    LOG.assertTrue(counter > 0, "myBackgroundOperationCounter was " + counter + " while should have been > 0");
  }

  @Override
  public boolean isBackgroundVcsOperationRunning() {
    return myBackgroundOperationCounter.get() > 0;
  }

  @Override
  public List<VirtualFile> getRootsUnderVcsWithoutFiltering(@NotNull AbstractVcs vcs) {
    return myMappings.getMappingsAsFilesUnderVcs(vcs);
  }

  @Override
  public VirtualFile @NotNull [] getRootsUnderVcs(@NotNull AbstractVcs vcs) {
    return MappingsToRoots.getRootsUnderVcs(myProject, myMappings, vcs);
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
  public VcsRoot @NotNull [] getAllVcsRoots() {
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
  public String getConsolidatedVcsName() {
    AbstractVcs singleVcs = getSingleVCS();
    return singleVcs != null ? singleVcs.getShortNameWithMnemonic() : VcsBundle.message("vcs.generic.name.with.mnemonic");
  }

  @Override
  public void notifyDirectoryMappingChanged() {
    fireDirectoryMappingsChanged();
  }

  @Override
  public void loadState(@NotNull Element element) {
    final List<VcsDirectoryMapping> mappingsList = new ArrayList<>();
    boolean haveNonEmptyMappings = false;
    for (Element child : element.getChildren(ELEMENT_MAPPING)) {
      String vcsName = child.getAttributeValue(ATTRIBUTE_VCS);
      String directory = child.getAttributeValue(ATTRIBUTE_DIRECTORY);
      if (directory == null) continue;

      VcsRootSettings rootSettings = null;
      Element rootSettingsElement = child.getChild(ELEMENT_ROOT_SETTINGS);
      if (rootSettingsElement != null) {
        String className = rootSettingsElement.getAttributeValue(ATTRIBUTE_CLASS);
        AbstractVcs vcsInstance = vcsName == null ? null : AllVcses.getInstance(myProject).getByName(vcsName);
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

      VcsDirectoryMapping mapping = new VcsDirectoryMapping(directory, vcsName, rootSettings);
      mappingsList.add(mapping);

      haveNonEmptyMappings |= !mapping.isDefaultMapping();
    }
    boolean defaultProject = Boolean.TRUE.toString().equals(element.getAttributeValue(ATTRIBUTE_DEFAULT_PROJECT));
    // run autodetection if there's no VCS in default project and
    if (haveNonEmptyMappings || !defaultProject) {
      myMappingsLoaded = true;
    }
    myMappings.setDirectoryMappingsFromConfig(mappingsList);
  }

  @Override
  public @NotNull Element getState() {
    Element element = new Element("state");
    if (myProject.isDefault()) {
      element.setAttribute(ATTRIBUTE_DEFAULT_PROJECT, Boolean.TRUE.toString());
    }
    for (VcsDirectoryMapping mapping : getDirectoryMappings()) {
      VcsRootSettings rootSettings = mapping.getRootSettings();
      if (rootSettings == null && mapping.isDefaultMapping() && mapping.isNoneMapping()) {
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
    return element;
  }

  /**
   * Returns 'true' during initial project setup, ie:
   * <ul>
   * <li> There are no explicitly configured mappings ({@link #setDirectoryMapping} vs {@link #setAutoDirectoryMappings})
   * <li> There are no mappings inherited from "Default Project" configuration (excluding &lt;Project&gt; mappings) ({@link #myMappingsLoaded})
   * <li> Project was not reopened a second time ({@link #ATTRIBUTE_DEFAULT_PROJECT})
   * </ul>
   */
  public boolean needAutodetectMappings() {
    return !myMappingsLoaded &&
           !OptionsAndConfirmationsHolder.getInstance(myProject).haveLegacyVcsConfiguration();
  }

  /**
   * Used to guess VCS for automatic mapping through a look into a working copy
   */
  @Override
  public @Nullable AbstractVcs findVersioningVcs(@NotNull VirtualFile file) {
    Set<String> checkedVcses = new HashSet<>();

    for (VcsRootChecker checker : VcsRootChecker.EXTENSION_POINT_NAME.getExtensionList()) {
      String vcsName = checker.getSupportedVcs().getName();
      checkedVcses.add(vcsName);

      if (checker.isRoot(file)) {
        return findVcsByName(vcsName);
      }
    }

    String foundVcs = null;
    for (VcsDescriptor vcsDescriptor : getAllVcss()) {
      String vcsName = vcsDescriptor.getName();
      if (checkedVcses.contains(vcsName)) continue;

      if (vcsDescriptor.probablyUnderVcs(file)) {
        if (foundVcs != null) {
          return null;
        }
        foundVcs = vcsName;
      }
    }
    return findVcsByName(foundVcs);
  }

  @Override
  public @NotNull VcsRootChecker getRootChecker(@NotNull AbstractVcs vcs) {
    for (VcsRootChecker checker : VcsRootChecker.EXTENSION_POINT_NAME.getIterable()) {
      if (checker.getSupportedVcs().equals(vcs.getKeyInstanceMethod())) {
        return checker;
      }
    }
    return new DefaultVcsRootChecker(vcs, getDescriptor(vcs.getName()));
  }

  @Override
  public CheckoutProvider.Listener getCompositeCheckoutListener() {
    return new CompositeCheckoutListener(myProject);
  }

  @Override
  public void fireDirectoryMappingsChanged() {
    if (myProject.isOpen() && !myProject.isDisposed()) {
      myMappings.notifyMappingsChanged();
    }
  }

  /**
   * @return VCS name for default mapping, if any
   */
  @Override
  public @Nullable String haveDefaultMapping() {
    return myMappings.haveDefaultMapping();
  }

  @CalledInAny
  boolean isBackgroundTaskRunning(Object @NotNull ... keys) {
    return myBackgroundRunningTasks.contains(new ActionKey(keys));
  }

  @RequiresEdt
  void startBackgroundTask(Object @NotNull ... keys) {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.assertTrue(myBackgroundRunningTasks.add(new ActionKey(keys)));
  }

  @RequiresEdt
  void stopBackgroundTask(Object @NotNull ... keys) {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.assertTrue(myBackgroundRunningTasks.remove(new ActionKey(keys)));
  }

  /**
   * @see ProjectLevelVcsManager#runAfterInitialization(Runnable)
   * @see VcsStartupActivity
   */
  public void addInitializationRequest(@NotNull VcsInitObject vcsInitObject, @NotNull Runnable runnable) {
    VcsInitialization.Companion.getInstance(myProject).add(vcsInitObject, runnable);
  }

  @Override
  public void runAfterInitialization(@NotNull Runnable runnable) {
    addInitializationRequest(VcsInitObject.AFTER_COMMON, runnable);
  }

  @Override
  public boolean isFileInContent(@Nullable VirtualFile vf) {
    if (vf == null) {
      return false;
    }
    return ReadAction.compute(() -> {
      if (!vf.isValid()) return false;
      FileIndexFacade fileIndex = FileIndexFacade.getInstance(myProject);
      boolean isUnderProject = isFileInBaseDir(vf) ||
                               isInDirectoryBasedRoot(vf) ||
                               hasExplicitMapping(vf) ||
                               fileIndex.isInContent(vf) ||
                               (!Registry.is("ide.hide.excluded.files") && fileIndex.isExcludedFile(vf));
      return isUnderProject && !isIgnored(vf);
    });
  }

  @Override
  public boolean isIgnored(@NotNull VirtualFile vf) {
    return ReadAction.compute(() -> {
      if (myProject.isDisposed() || myProject.isDefault()) return false;
      if (!vf.isValid()) return false;

      if (Registry.is("ide.hide.excluded.files")) {
        return FileIndexFacade.getInstance(myProject).isExcludedFile(vf);
      }
      else {
        return FileIndexFacade.getInstance(myProject).isUnderIgnored(vf);
      }
    });
  }

  @Override
  public boolean isIgnored(@NotNull FilePath filePath) {
    return ReadAction.compute(() -> {
      if (myProject.isDisposed() || myProject.isDefault()) return false;

      if (Registry.is("ide.hide.excluded.files")) {
        VirtualFile vf = VcsImplUtil.findValidParentAccurately(filePath);
        return vf != null && FileIndexFacade.getInstance(myProject).isExcludedFile(vf);
      }
      else {
        // WARN: might differ from 'myExcludedIndex.isUnderIgnored' if whole content root is under folder with 'ignored' name.
        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        for (String name : StringUtil.tokenize(filePath.getPath(), "/")) {
          if (fileTypeManager.isFileIgnored(name)) {
            return true;
          }
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
    if (baseDir == null) {
      return false;
    }

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
    return VcsCacheManager.getInstance(myProject).getVcsHistoryCache();
  }

  @Override
  public ContentRevisionCache getContentRevisionCache() {
    return VcsCacheManager.getInstance(myProject).getContentRevisionCache();
  }

  @TestOnly
  public void waitForInitialized() {
    VcsInitialization.Companion.getInstance(myProject).waitFinished();
  }

  @Override
  public void showConsole(@Nullable Runnable then) {
    VcsConsoleTabService.getInstance(myProject).showConsoleTab(true, null);
  }

  @Override
  public void scrollConsoleToTheEnd() {
    VcsConsoleTabService.getInstance(myProject).showConsoleTabAndScrollToTheEnd();
  }

  private static class ActionKey {
    private final Object[] myObjects;

    ActionKey(Object @NotNull ... objects) {
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

  static final class ActivateVcsesStartupActivity implements VcsStartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      getInstanceImpl(project).myMappings.activateActiveVcses();
    }

    @Override
    public int getOrder() {
      return VcsInitObject.MAPPINGS.getOrder();
    }
  }

  static final class TrustListener implements TrustedProjectsListener {
    @Override
    public void onProjectTrusted(@NotNull Project project) {
      getInstanceImpl(project).updateMappedVcsesImmediately();
    }
  }
}
