// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.filename.UniqueNameBuilder;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.util.paths.FilePathMapping;
import com.intellij.openapi.vcs.util.paths.VirtualFileMapping;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.Alarm;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Functions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.update.DisposableUpdate;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;

public final class NewMappings implements Disposable {
  private static final Comparator<MappedRoot> ROOT_COMPARATOR = Comparator.comparing(it -> it.root.getPath());
  private static final Comparator<VcsDirectoryMapping> MAPPINGS_COMPARATOR = Comparator.comparing(VcsDirectoryMapping::getDirectory);

  private static final Logger LOG = Logger.getInstance(NewMappings.class);
  private final Object myUpdateLock = new Object();

  private FileWatchRequestsManager myFileWatchRequestsManager;

  private final ProjectLevelVcsManager myVcsManager;
  private final Project myProject;

  private volatile List<VcsDirectoryMapping> myMappings = Collections.emptyList(); // sorted by MAPPINGS_COMPARATOR

  private @NotNull Disposable myFilePointerDisposable = Disposer.newDisposable();
  private volatile List<MappedRoot> myMappedRoots = Collections.emptyList(); // sorted by ROOT_COMPARATOR
  private volatile RootMapping myMappedRootsMapping = new RootMapping(Collections.emptyList());
  private volatile Map<VirtualFile, @NlsSafe String> myMappedRootShortNames = Collections.emptyMap();

  private volatile List<AbstractVcs> myActiveVcses = Collections.emptyList();
  private volatile boolean myActivated = false;

  private final @NotNull MergingUpdateQueue myRootUpdateQueue;
  private final VirtualFilePointerListener myFilePointerListener;

  public NewMappings(@NotNull Project project, @NotNull ProjectLevelVcsManagerImpl vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
    myFileWatchRequestsManager = new FileWatchRequestsManager(myProject, this);

    myRootUpdateQueue = new MergingUpdateQueue("NewMappings", 1000, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD)
      .usePassThroughInUnitTestMode();

    myFilePointerListener = new VirtualFilePointerListener() {
      @Override
      public void validityChanged(VirtualFilePointer @NotNull [] pointers) {
        scheduleMappedRootsUpdate();
      }
    };
    VcsRootChecker.EXTENSION_POINT_NAME.addChangeListener(() -> scheduleMappedRootsUpdate(), project);
  }

  @TestOnly
  public void setFileWatchRequestsManager(FileWatchRequestsManager fileWatchRequestsManager) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myFileWatchRequestsManager = fileWatchRequestsManager;
  }

  public AbstractVcs @NotNull [] getActiveVcses() {
    return myActiveVcses.toArray(new AbstractVcs[0]);
  }

  public boolean hasActiveVcss() {
    return !myActiveVcses.isEmpty();
  }

  public boolean isActivated() {
    return myActivated;
  }

  public void activateActiveVcses() {
    synchronized (myUpdateLock) {
      if (myActivated) return;
      myActivated = true;
      LOG.debug("activated");
    }
    updateActiveVcses(true);
    updateMappedRoots(true);
  }

  /**
   * @return {@link #myActivated} value
   */
  private boolean updateActiveVcses(boolean forceFireEvent) {
    MyVcsActivator activator =
      ReadAction.compute(() -> {
        synchronized (myUpdateLock) {
          return myActivated ? createVcsActivator() : null;
        }
      });

    if (activator != null) {
      boolean wasChanged = activator.activate();
      if (forceFireEvent || wasChanged) {
        BackgroundTaskUtil.syncPublisher(myProject, ProjectLevelVcsManagerEx.VCS_ACTIVATED).vcsesActivated(myActiveVcses);
      }
    }

    return activator != null;
  }

  public void setMapping(@NotNull String path, @Nullable String activeVcsName) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("setMapping path = '" + path + "' vcs = " + activeVcsName, new Throwable());
    }
    final VcsDirectoryMapping newMapping = new VcsDirectoryMapping(path, activeVcsName);

    List<VcsDirectoryMapping> newMappings = new ArrayList<>(myMappings);
    newMappings.removeIf(mapping -> Objects.equals(mapping.getDirectory(), newMapping.getDirectory()));
    newMappings.add(newMapping);

    updateVcsMappings(newMappings);
  }

  @TestOnly
  public void waitMappedRootsUpdate() {
    myRootUpdateQueue.flush();
  }

  @TestOnly
  public void freezeMappedRootsUpdate(@NotNull Disposable disposable) {
    myRootUpdateQueue.setPassThrough(false);
    myRootUpdateQueue.suspend();
    Disposer.register(disposable, () -> {
      myRootUpdateQueue.setPassThrough(false);
      myRootUpdateQueue.resume();
    });
  }

  public void updateMappedVcsesImmediately() {
    LOG.debug("updateMappingsImmediately");

    if (!updateActiveVcses(false)) return;

    synchronized (myUpdateLock) {
      Disposer.dispose(myFilePointerDisposable);
      myFilePointerDisposable = Disposer.newDisposable();

      myMappedRoots = Collections.emptyList();
      myMappedRootsMapping = new RootMapping(Collections.emptyList());
      myMappedRootShortNames = Collections.emptyMap();

      dumpMappedRootsToLog();
    }
    notifyMappingsChanged();

    scheduleMappedRootsUpdate();
  }

  public void scheduleMappedRootsUpdate() {
    myRootUpdateQueue.queue(new DisposableUpdate(this, "update") {
      @Override
      public void doRun() {
        updateMappedRoots(true);
      }
    });
  }

  private void updateVcsMappings(@NotNull List<VcsDirectoryMapping> mappings) {
    myRootUpdateQueue.cancelAllUpdates();

    List<VcsDirectoryMapping> newMappings = List.copyOf(ContainerUtil.sorted(removeDuplicates(mappings), MAPPINGS_COMPARATOR));
    synchronized (myUpdateLock) {
      boolean mappingsChanged = !myMappings.equals(newMappings);
      if (!mappingsChanged) return; // mappings are up-to-date

      myMappings = newMappings;

      dumpMappingsToLog();
    }

    updateActiveVcses(false);

    if (ApplicationManager.getApplication().isDispatchThread() &&
        ContainerUtil.exists(newMappings, it -> it.isDefaultMapping())) {
      updateMappedRootsFast();
      scheduleMappedRootsUpdate();
    }
    else {
      updateMappedRoots(false);
    }

    notifyMappingsChanged();
  }

  private void updateMappedRoots(boolean fireMappingsChangedEvent) {
    myRootUpdateQueue.cancelAllUpdates();

    if (!myActivated) return;
    LOG.debug("updateMappedRoots");

    List<VcsDirectoryMapping> mappings = myMappings;
    Mappings newMappedRoots = collectMappedRoots(mappings, null);

    setNewMappedRoots(mappings, newMappedRoots, fireMappingsChangedEvent);
  }

  private void updateMappedRootsFast() {
    if (!myActivated) return;
    LOG.debug("updateMappedRootsFast");

    List<VcsDirectoryMapping> mappings;
    List<MappedRoot> mappedRoots;
    synchronized (myUpdateLock) {
      mappings = myMappings;
      mappedRoots = myMappedRoots;
    }
    Mappings newMappedRoots = collectMappedRoots(mappings, mappedRoots);

    setNewMappedRoots(mappings, newMappedRoots, false);
  }

  private void setNewMappedRoots(@NotNull List<VcsDirectoryMapping> mappings,
                                 @NotNull Mappings newMappedRoots,
                                 boolean fireMappingsChangedEvent) {
    Map<VirtualFile, @NlsSafe String> newMappedRootNames = buildMappingShortNameMap(myProject, newMappedRoots.mappedRoots);

    boolean mappedRootsChanged;
    synchronized (myUpdateLock) {
      if (myMappings != mappings) {
        Disposer.dispose(newMappedRoots.filePointerDisposable);
        return;
      }

      Disposer.dispose(myFilePointerDisposable);
      myFilePointerDisposable = newMappedRoots.filePointerDisposable;

      mappedRootsChanged = !myMappedRoots.equals(newMappedRoots.mappedRoots);
      if (mappedRootsChanged) {
        myMappedRoots = newMappedRoots.mappedRoots;
        myMappedRootsMapping = new RootMapping(newMappedRoots.mappedRoots);
        myMappedRootShortNames = newMappedRootNames;

        dumpMappedRootsToLog();
      }
    }

    if (fireMappingsChangedEvent && mappedRootsChanged) notifyMappingsChanged();
  }

  private static @NotNull Map<VirtualFile, @NlsSafe String> buildMappingShortNameMap(@NotNull Project project,
                                                                                     @NotNull List<MappedRoot> roots) {
    String basePath = project.getBasePath();
    String builderRoot = basePath != null ? FileUtil.toSystemIndependentName(basePath) : "";

    MultiMap<String, VirtualFile> nameCollisionMap = new MultiMap<>();
    for (MappedRoot mappedRoot : roots) {
      VirtualFile root = mappedRoot.root;
      nameCollisionMap.putValue(root.getName(), root);
    }

    Map<VirtualFile, @NlsSafe String> result = new HashMap<>();
    for (Map.Entry<String, Collection<VirtualFile>> entry : nameCollisionMap.entrySet()) {
      Collection<VirtualFile> collisionRoots = entry.getValue();
      if (collisionRoots.size() == 1) {
        // UniqueNameBuilder doesn't support unique names
        for (VirtualFile root : collisionRoots) {
          result.put(root, root.getName());
        }
      }
      else {
        UniqueNameBuilder<VirtualFile> builder = new UniqueNameBuilder<>(builderRoot, File.separator);
        for (VirtualFile root : collisionRoots) {
          builder.addPath(root, root.getPath());
        }
        for (VirtualFile root : collisionRoots) {
          result.put(root, StringUtil.nullize(builder.getShortPath(root)));
        }
      }
    }
    return result;
  }

  private void refreshMainMenu() {
    ApplicationManager.getApplication().invokeLater(() -> {
      ProjectFrameHelper frame = WindowManagerEx.getInstanceEx().getFrameHelper(myProject);
      if (frame != null) {
        // GitToolbarWidgetFactory handles update in a new UI
        if (ExperimentalUI.isNewUI()) {
          frame.rootPane.updateMainMenuActions();
        }
        else {
          frame.updateView();
        }
      }
    }, myProject.getDisposed());
  }

  /**
   * Take last mapping in collection in case of duplicates.
   */
  private static @NotNull List<VcsDirectoryMapping> removeDuplicates(@NotNull List<VcsDirectoryMapping> mappings) {
    List<VcsDirectoryMapping> newMapping = new ArrayList<>();
    Set<String> paths = new HashSet<>();

    for (VcsDirectoryMapping mapping : ContainerUtil.reverse(mappings)) {
      if (paths.add(mapping.getDirectory())) {
        newMapping.add(mapping);
      }
    }
    return newMapping;
  }

  private @NotNull Mappings collectMappedRoots(@NotNull List<VcsDirectoryMapping> mappings,
                                               @Nullable List<MappedRoot> reuseMappedRoots) {
    Map<VirtualFile, MappedRoot> mappedRoots = new HashMap<>();
    Disposable pointerDisposable = Disposer.newDisposable();

    if (!TrustedProjects.isTrusted(myProject)) {
      return new Mappings(Collections.emptyList(), pointerDisposable);
    }

    try {
      // direct mappings have priority over <Project> mappings
      for (VcsDirectoryMapping mapping : mappings) {
        if (mapping.isDefaultMapping()) {
          continue;
        }

        MappedRoot mappedRoot = findDirectMappingFor(mapping, pointerDisposable);
        if (mappedRoot != null) mappedRoots.putIfAbsent(mappedRoot.root, mappedRoot);
      }

      for (VcsDirectoryMapping mapping : mappings) {
        if (!mapping.isDefaultMapping()) {
          continue;
        }

        List<MappedRoot> defaultMappings;
        if (reuseMappedRoots != null) {
          defaultMappings = reuseDefaultMappingsFrom(mapping, reuseMappedRoots, pointerDisposable);
        }
        else {
          Set<VirtualFile> directMappingDirs = ContainerUtil.map2Set(mappedRoots.values(), it -> it.root);
          defaultMappings = findDefaultMappingsFor(mapping, directMappingDirs, pointerDisposable);
        }
        for (MappedRoot mappedRoot : defaultMappings) {
          mappedRoots.putIfAbsent(mappedRoot.root, mappedRoot);
        }
      }

      List<MappedRoot> result = ContainerUtil.sorted(mappedRoots.values(), ROOT_COMPARATOR);

      for (MappedRoot root : result) {
        if (myVcsManager.isIgnored(VcsUtil.getFilePath(root.root))) {
          LOG.warn("Root mapping is under ignored root: " + root.root);
        }
      }

      return new Mappings(result, pointerDisposable);
    }
    catch (Throwable e) {
      Disposer.dispose(pointerDisposable);
      ExceptionUtil.rethrow(e);
      return null;
    }
  }

  @Nullable
  private MappedRoot findDirectMappingFor(@NotNull VcsDirectoryMapping mapping,
                                          @NotNull Disposable pointerDisposable) {
    AbstractVcs vcs = getMappingsVcs(mapping);
    String rootPath = mapping.getDirectory();

    return ReadAction.compute(() -> {
      VirtualFilePointerManager.getInstance().create(VfsUtilCore.pathToUrl(rootPath), pointerDisposable, myFilePointerListener);

      VirtualFile vcsRoot = LocalFileSystem.getInstance().findFileByPath(rootPath);
      if (vcsRoot == null || !vcsRoot.isDirectory()) {
        return null;
      }

      if (checkMappedRoot(vcs, vcsRoot)) {
        return new MappedRoot(vcs, mapping, vcsRoot);
      }
      else {
        return new MappedRoot(null, mapping, vcsRoot);
      }
    });
  }

  @NotNull
  private List<MappedRoot> findDefaultMappingsFor(@NotNull VcsDirectoryMapping mapping,
                                                  @NotNull Set<VirtualFile> directMappingDirs,
                                                  @NotNull Disposable pointerDisposable) {
    AbstractVcs vcs = getMappingsVcs(mapping);
    if (vcs == null) {
      return Collections.emptyList();
    }

    Collection<VirtualFile> defaultRoots = detectDefaultRootsFor(vcs,
                                                                 DefaultVcsRootPolicy.getInstance(myProject).getDefaultVcsRoots(),
                                                                 directMappingDirs);

    List<MappedRoot> result = new ArrayList<>();
    ReadAction.run(() -> {
      for (VirtualFile vcsRoot : defaultRoots) {
        if (vcsRoot != null && vcsRoot.isDirectory()) {
          VirtualFilePointerManager.getInstance().create(vcsRoot, pointerDisposable, myFilePointerListener);
          result.add(new MappedRoot(vcs, mapping, vcsRoot));
        }
      }
    });
    return result;
  }

  @NotNull
  private List<MappedRoot> reuseDefaultMappingsFrom(@NotNull VcsDirectoryMapping mapping,
                                                    @NotNull List<MappedRoot> oldMappedRoots,
                                                    @NotNull Disposable pointerDisposable) {
    // Pretend that mappings did not change at first, and "<Project>" mappings has detected all the roots that were used before.
    // This prevents such roots from being temporally unregistered if they will be detected later by the proper-and-slow logic.
    // Which in its turn allows preserving Change-to-Changelist mappings and
    // avoiding sporadic "file not under VCS root anymore" errors in the middle of operation.

    // For example, if a "$PROJECT_DIR$" was replaced with a "<Project>" mapping as a part of shelve-unshelve operation.

    List<MappedRoot> result = new ArrayList<>();
    ReadAction.run(() -> {
      List<MappedRoot> oldMappings = ContainerUtil.filter(oldMappedRoots, root -> root.mapping.getVcs().equals(mapping.getVcs()));
      for (MappedRoot root : oldMappings) {
        VirtualFilePointerManager.getInstance().create(root.root, pointerDisposable, myFilePointerListener);
        result.add(new MappedRoot(root.vcs, mapping, root.root));
      }
    });
    return result;
  }

  private @Nullable AbstractVcs getMappingsVcs(@NotNull VcsDirectoryMapping mapping) {
    return AllVcses.getInstance(myProject).getByName(mapping.getVcs());
  }

  private boolean checkMappedRoot(@Nullable AbstractVcs vcs, @NotNull VirtualFile vcsRoot) {
    try {
      if (vcs == null) return false;
      VcsRootChecker rootChecker = myVcsManager.getRootChecker(vcs);
      return rootChecker.validateRoot(vcsRoot);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return false;
    }
  }

  private @NotNull Collection<VirtualFile> detectDefaultRootsFor(@NotNull AbstractVcs vcs,
                                                                 @NotNull Collection<VirtualFile> projectRoots,
                                                                 @NotNull Set<VirtualFile> mappedDirs) {
    try {
      if (vcs.needsLegacyDefaultMappings()) return projectRoots;

      VcsRootChecker rootChecker = myVcsManager.getRootChecker(vcs);

      Collection<VirtualFile> checkerFiles = rootChecker.detectProjectMappings(myProject, projectRoots, mappedDirs);
      if (checkerFiles != null) return checkerFiles;

      return VcsDefaultMappingUtils.detectProjectMappings(myProject, rootChecker, projectRoots, mappedDirs);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }

  public void notifyMappingsChanged() {
    BackgroundTaskUtil.syncPublisher(myProject, ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED).directoryMappingChanged();
    myFileWatchRequestsManager.ping();
  }

  private void dumpMappingsToLog() {
    for (VcsDirectoryMapping mapping : myMappings) {
      String path = mapping.isDefaultMapping() ? "<Project>" : mapping.getDirectory();
      String vcs = mapping.getVcs();
      LOG.info(String.format("VCS Root: [%s] - [%s]", vcs, path));
    }
  }

  private void dumpMappedRootsToLog() {
    if (LOG.isDebugEnabled()) {
      for (MappedRoot root : myMappedRoots) {
        LOG.debug(String.format("Mapped Root: [%s] - [%s]", root.vcs, root.root.getPath()));
      }
    }
    else if (haveDefaultMapping() != null) {
      LOG.info("Mapped Roots: " + myMappedRoots.size());
    }
  }

  public void setDirectoryMappings(@NotNull List<VcsDirectoryMapping> items) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("setDirectoryMappings, size: " + items.size(), new Throwable());
    }

    updateVcsMappings(items);
  }

  public @Nullable MappedRoot getMappedRootFor(@Nullable VirtualFile file) {
    if (file == null || !file.isInLocalFileSystem()) return null;
    if (myMappedRoots.isEmpty()) return null;
    if (myVcsManager.isIgnored(file)) return null;

    return myMappedRootsMapping.getRootFor(file);
  }

  public @Nullable MappedRoot getMappedRootFor(@Nullable FilePath file) {
    if (file == null || file.isNonLocal()) return null;
    if (myMappedRoots.isEmpty()) return null;
    if (myVcsManager.isIgnored(file)) return null;

    return myMappedRootsMapping.getRootFor(file);
  }

  public @NotNull List<MappedRoot> getAllMappedRoots() {
    return myMappedRoots;
  }

  public @Nullable @NlsSafe String getShortNameFor(@Nullable VirtualFile file) {
    return myMappedRootShortNames.get(file);
  }

  public @NotNull List<VirtualFile> getMappingsAsFilesUnderVcs(@NotNull AbstractVcs vcs) {
    return ContainerUtil.mapNotNull(myMappedRoots, root -> {
      return vcs.equals(root.vcs) ? root.root : null;
    });
  }

  @Override
  public void dispose() {
    LOG.debug("disposed");

    MyVcsActivator activator;
    synchronized (myUpdateLock) {
      Disposer.dispose(myFilePointerDisposable);
      myMappings = Collections.emptyList();
      myMappedRoots = Collections.emptyList();
      myMappedRootsMapping = new RootMapping(Collections.emptyList());
      myMappedRootShortNames = Collections.emptyMap();
      myFilePointerDisposable = Disposer.newDisposable();
      activator = createVcsActivator();
    }
    activator.activate();
  }

  public List<VcsDirectoryMapping> getDirectoryMappings() {
    return myMappings;
  }

  public List<VcsDirectoryMapping> getDirectoryMappings(String vcsName) {
    return ContainerUtil.filter(myMappings, mapping -> Objects.equals(mapping.getVcs(), vcsName));
  }

  public @Nullable String haveDefaultMapping() {
    VcsDirectoryMapping defaultMapping = ContainerUtil.find(myMappings, mapping -> mapping.isDefaultMapping());
    return defaultMapping != null ? defaultMapping.getVcs() : null;
  }

  public boolean isEmpty() {
    return ContainerUtil.all(myMappings, mapping -> mapping.isNoneMapping());
  }

  public void removeDirectoryMapping(@NotNull VcsDirectoryMapping mapping) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("remove mapping: " + mapping.getDirectory(), new Throwable());
    }

    List<VcsDirectoryMapping> newMappings = new ArrayList<>(myMappings);
    newMappings.remove(mapping);

    updateVcsMappings(newMappings);
  }

  public void cleanupMappings() {
    LocalFileSystem lfs = LocalFileSystem.getInstance();

    List<VcsDirectoryMapping> oldMappings = new ArrayList<>(getDirectoryMappings());

    List<VcsDirectoryMapping> filteredMappings = new ArrayList<>();

    VcsDirectoryMapping defaultMapping = ContainerUtil.find(oldMappings, it -> it.isDefaultMapping());
    if (defaultMapping != null) {
      oldMappings.remove(defaultMapping);
      filteredMappings.add(defaultMapping);
    }

    MultiMap<String, VcsDirectoryMapping> groupedMappings = new MultiMap<>();
    for (VcsDirectoryMapping mapping : oldMappings) {
      groupedMappings.putValue(mapping.getVcs(), mapping);
    }

    for (Map.Entry<String, Collection<VcsDirectoryMapping>> entry : groupedMappings.entrySet()) {
      String vcsName = entry.getKey();
      Collection<VcsDirectoryMapping> mappings = entry.getValue();

      List<Pair<VirtualFile, VcsDirectoryMapping>> objects = ContainerUtil.mapNotNull(mappings, dm -> {
        VirtualFile vf = lfs.refreshAndFindFileByPath(dm.getDirectory());
        return vf == null ? null : Pair.create(vf, dm);
      });

      if (StringUtil.isEmptyOrSpaces(vcsName)) {
        filteredMappings.addAll(ContainerUtil.map(objects, Functions.pairSecond()));
      }
      else {
        AbstractVcs vcs = myVcsManager.findVcsByName(vcsName);
        if (vcs == null) {
          VcsBalloonProblemNotifier.showOverChangesView(
            myProject,
            VcsBundle.message("impl.notification.content.vcs.plugin.not.found.for.mapping.to", vcsName),
            MessageType.ERROR
          );
          filteredMappings.addAll(mappings);
        }
        else {
          filteredMappings.addAll(ContainerUtil.map(vcs.filterUniqueRoots(objects, pair -> pair.getFirst()), Functions.pairSecond()));
        }
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("NewMappings.cleanupMappings", getDirectoryMappings(), filteredMappings);
    }

    updateVcsMappings(filteredMappings);
  }

  private @NotNull MyVcsActivator createVcsActivator() {
    Set<AbstractVcs> newVcses = !myProject.isDisposed() && TrustedProjects.isTrusted(myProject)
                                ? ContainerUtil.map2SetNotNull(myMappings, this::getMappingsVcs)
                                : Collections.emptySet();

    List<AbstractVcs> oldVcses = myActiveVcses;
    myActiveVcses = List.copyOf(newVcses);

    refreshMainMenu();

    Collection<AbstractVcs> toAdd = ContainerUtil.subtract(myActiveVcses, oldVcses);
    Collection<AbstractVcs> toRemove = ContainerUtil.subtract(oldVcses, myActiveVcses);

    return new MyVcsActivator(toAdd, toRemove);
  }

  private static final class MyVcsActivator {
    private final @NotNull Collection<? extends AbstractVcs> myAddVcses;
    private final @NotNull Collection<? extends AbstractVcs> myRemoveVcses;

    private MyVcsActivator(@NotNull Collection<? extends AbstractVcs> addVcses,
                           @NotNull Collection<? extends AbstractVcs> removeVcses) {
      myAddVcses = addVcses;
      myRemoveVcses = removeVcses;
    }

    public boolean activate() {
      return ProgressManager.getInstance().computeInNonCancelableSection(() -> {
        for (AbstractVcs vcs : myAddVcses) {
          try {
            vcs.doActivate();
          }
          catch (VcsException e) {
            LOG.error(e);
          }
        }
        for (AbstractVcs vcs : myRemoveVcses) {
          try {
            vcs.doDeactivate();
          }
          catch (VcsException e) {
            LOG.error(e);
          }
        }
        return !myAddVcses.isEmpty() || !myRemoveVcses.isEmpty();
      });
    }
  }

  public boolean haveActiveVcs(final String name) {
    return ContainerUtil.exists(myActiveVcses, vcs -> Objects.equals(vcs.getName(), name));
  }

  public void beingUnregistered(final String name) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("beingUnregistered " + name, new Throwable());
    }

    List<VcsDirectoryMapping> newMappings = new ArrayList<>(myMappings);
    newMappings.removeIf(mapping -> Objects.equals(mapping.getVcs(), name));

    updateVcsMappings(newMappings);
  }

  public static final class MappedRoot {
    public final @Nullable AbstractVcs vcs;
    public final @NotNull VcsDirectoryMapping mapping;
    public final @NotNull VirtualFile root;

    private MappedRoot(@Nullable AbstractVcs vcs, @NotNull VcsDirectoryMapping mapping, @NotNull VirtualFile root) {
      this.vcs = vcs;
      this.mapping = mapping;
      this.root = root;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MappedRoot other = (MappedRoot)o;
      return Objects.equals(vcs, other.vcs) &&
             mapping.equals(other.mapping) &&
             root.equals(other.root);
    }

    @Override
    public int hashCode() {
      return Objects.hash(vcs, mapping, root);
    }
  }

  private static final class Mappings {
    public final @NotNull List<MappedRoot> mappedRoots;
    public final @NotNull Disposable filePointerDisposable;

    private Mappings(@NotNull List<MappedRoot> mappedRoots, @NotNull Disposable filePointerDisposable) {
      this.mappedRoots = mappedRoots;
      this.filePointerDisposable = filePointerDisposable;
    }
  }

  private static final class RootMapping {
    private final VirtualFileMapping<MappedRoot> myVFMap = new VirtualFileMapping<>();
    private final FilePathMapping<MappedRoot> myPathMapping = new FilePathMapping<>(SystemInfo.isFileSystemCaseSensitive);

    private RootMapping(@NotNull List<MappedRoot> mappedRoots) {
      for (MappedRoot root : mappedRoots) {
        myVFMap.add(root.root, root);
        myPathMapping.add(root.root.getPath(), root);
      }
    }

    public @Nullable MappedRoot getRootFor(@NotNull VirtualFile file) {
      return myVFMap.getMappingFor(file);
    }

    public @Nullable MappedRoot getRootFor(@NotNull FilePath filePath) {
      return myPathMapping.getMappingFor(filePath.getPath());
    }
  }
}
