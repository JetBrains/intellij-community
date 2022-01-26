// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
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

import java.util.*;

public final class NewMappings implements Disposable {
  private static final Comparator<MappedRoot> ROOT_COMPARATOR = Comparator.comparing(it -> it.root.getPath());
  private static final Comparator<VcsDirectoryMapping> MAPPINGS_COMPARATOR = Comparator.comparing(VcsDirectoryMapping::getDirectory);

  private final static Logger LOG = Logger.getInstance(NewMappings.class);
  private final Object myUpdateLock = new Object();

  private FileWatchRequestsManager myFileWatchRequestsManager;

  private final ProjectLevelVcsManager myVcsManager;
  private final Project myProject;

  @NotNull private Disposable myFilePointerDisposable = Disposer.newDisposable();
  private volatile List<VcsDirectoryMapping> myMappings = Collections.emptyList(); // sorted by MAPPINGS_COMPARATOR
  private volatile List<MappedRoot> myMappedRoots = Collections.emptyList(); // sorted by ROOT_COMPARATOR
  private volatile RootMapping myMappedRootsMapping = new RootMapping(Collections.emptyList());
  private volatile List<AbstractVcs> myActiveVcses = Collections.emptyList();
  private volatile boolean myActivated = false;

  @NotNull private final MergingUpdateQueue myRootUpdateQueue;
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

  public void activateActiveVcses() {
    synchronized (myUpdateLock) {
      if (myActivated) return;
      myActivated = true;
      LOG.debug("activated");
    }
    updateActiveVcses();
    updateMappedRoots(true);
  }

  /**
   * @return {@link #myActivated} value
   */
  private boolean updateActiveVcses() {
    MyVcsActivator activator =
      ReadAction.compute(() -> {
        synchronized (myUpdateLock) {
          return myActivated ? createVcsActivator() : null;
        }
      });
    if (activator != null) {
      activator.activate();
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

  public void updateMappedVcsesImmediately() {
    LOG.debug("updateMappingsImmediately");

    if (!updateActiveVcses()) return;

    synchronized (myUpdateLock) {
      Disposer.dispose(myFilePointerDisposable);
      myFilePointerDisposable = Disposer.newDisposable();

      myMappedRoots = Collections.emptyList();
      myMappedRootsMapping = new RootMapping(Collections.emptyList());

      dumpMappedRootsToLog();
    }
    mappingsChanged();

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

  private void updateVcsMappings(@NotNull Collection<? extends VcsDirectoryMapping> mappings) {
    myRootUpdateQueue.cancelAllUpdates();

    List<VcsDirectoryMapping> newMappings = Collections
      .unmodifiableList(ContainerUtil.sorted(removeDuplicates(mappings), MAPPINGS_COMPARATOR));
    synchronized (myUpdateLock) {
      boolean mappingsChanged = !myMappings.equals(newMappings);
      if (!mappingsChanged) return; // mappings are up-to-date

      myMappings = newMappings;

      dumpMappingsToLog();
    }

    updateActiveVcses();

    updateMappedRoots(false);

    mappingsChanged();
  }

  private void updateMappedRoots(boolean fireMappingsChangedEvent) {
    myRootUpdateQueue.cancelAllUpdates();

    if (!myActivated) return;
    LOG.debug("updateMappedRoots");

    List<VcsDirectoryMapping> mappings = myMappings;
    Mappings newMappedRoots = collectMappedRoots(mappings);

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

        dumpMappedRootsToLog();
      }
    }

    if (fireMappingsChangedEvent && mappedRootsChanged) mappingsChanged();
  }

  private void refreshMainMenu() {
    ApplicationManager.getApplication().invokeLater(() -> {
      ProjectFrameHelper frame = WindowManagerEx.getInstanceEx().getFrameHelper(myProject);
      if (frame != null && !Disposer.isDisposed(frame)) {
        frame.updateView();
      }
    }, myProject.getDisposed());
  }

  @NotNull
  private static List<VcsDirectoryMapping> removeDuplicates(@NotNull Collection<? extends VcsDirectoryMapping> mappings) {
    List<VcsDirectoryMapping> newMapping = new ArrayList<>();
    Set<String> paths = new HashSet<>();

    for (VcsDirectoryMapping mapping : ContainerUtil.reverse(new ArrayList<>(mappings))) {
      // take last mapping in collection in case of duplicates
      if (paths.add(mapping.getDirectory())) {
        newMapping.add(mapping);
      }
    }
    return newMapping;
  }

  @NotNull
  private Mappings collectMappedRoots(@NotNull List<VcsDirectoryMapping> mappings) {
    VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();

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

        AbstractVcs vcs = getMappingsVcs(mapping);
        String rootPath = mapping.getDirectory();

        ReadAction.run(() -> {
          VirtualFile vcsRoot = LocalFileSystem.getInstance().findFileByPath(rootPath);

          if (vcsRoot != null && vcsRoot.isDirectory()) {
            if (checkMappedRoot(vcs, vcsRoot)) {
              mappedRoots.putIfAbsent(vcsRoot, new MappedRoot(vcs, mapping, vcsRoot));
            }
            else {
              mappedRoots.putIfAbsent(vcsRoot, new MappedRoot(null, mapping, vcsRoot));
            }
          }

          pointerManager.create(VfsUtilCore.pathToUrl(rootPath), pointerDisposable, myFilePointerListener);
        });
      }

      for (VcsDirectoryMapping mapping : mappings) {
        if (!mapping.isDefaultMapping()) {
          continue;
        }
        AbstractVcs vcs = getMappingsVcs(mapping);
        if (vcs == null) {
          continue;
        }

        Collection<VirtualFile> defaultRoots = detectDefaultRootsFor(vcs,
                                                                     DefaultVcsRootPolicy.getInstance(myProject).getDefaultVcsRoots(),
                                                                     ContainerUtil.map2Set(mappedRoots.values(), it -> it.root));

        ReadAction.run(() -> {
          for (VirtualFile vcsRoot : defaultRoots) {
            if (vcsRoot != null && vcsRoot.isDirectory()) {
              mappedRoots.putIfAbsent(vcsRoot, new MappedRoot(vcs, mapping, vcsRoot));
              pointerManager.create(vcsRoot, pointerDisposable, myFilePointerListener);
            }
          }
        });
      }

      List<MappedRoot> result = Collections.unmodifiableList(ContainerUtil.sorted(mappedRoots.values(), ROOT_COMPARATOR));

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
  private AbstractVcs getMappingsVcs(@NotNull VcsDirectoryMapping mapping) {
    return AllVcses.getInstance(myProject).getByName(mapping.getVcs());
  }

  private boolean checkMappedRoot(@Nullable AbstractVcs vcs, @NotNull VirtualFile vcsRoot) {
    try {
      if (vcs == null) return false;
      VcsRootChecker rootChecker = myVcsManager.getRootChecker(vcs);
      return rootChecker.validateRoot(vcsRoot.getPath());
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return false;
    }
  }

  @NotNull
  private Collection<VirtualFile> detectDefaultRootsFor(@NotNull AbstractVcs vcs,
                                                        @NotNull Collection<VirtualFile> projectRoots,
                                                        @NotNull Set<VirtualFile> mappedDirs) {
    try {
      if (vcs.needsLegacyDefaultMappings()) return projectRoots;

      DirectoryIndex directoryIndex = DirectoryIndex.getInstance(myProject);
      VcsRootChecker rootChecker = myVcsManager.getRootChecker(vcs);

      Map<VirtualFile, Boolean> checkedDirs = new HashMap<>();

      Set<VirtualFile> vcsRoots = new HashSet<>();
      for (VirtualFile f : projectRoots) {
        while (f != null) {
          if (vcsRoots.contains(f) || mappedDirs.contains(f)) break;

          if (isVcsRoot(rootChecker, checkedDirs, f)) {
            vcsRoots.add(f);
            break;
          }

          VirtualFile parent = f.getParent();
          if (parent != null && !isUnderProject(directoryIndex, parent)) {
            if (rootChecker.areChildrenValidMappings()) {
              while (parent != null) {
                if (vcsRoots.contains(parent) || mappedDirs.contains(parent)) break;

                if (isVcsRoot(rootChecker, checkedDirs, parent)) {
                  vcsRoots.add(f);
                  break;
                }

                parent = parent.getParent();
              }
            }

            break;
          }

          f = parent;
        }
      }
      return vcsRoots;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }

  private static boolean isVcsRoot(@NotNull VcsRootChecker rootChecker,
                                   @NotNull Map<VirtualFile, Boolean> checkedDirs,
                                   @NotNull VirtualFile file) {
    ProgressManager.checkCanceled();
    return checkedDirs.computeIfAbsent(file, key -> rootChecker.isRoot(key.getPath()));
  }

  private boolean isUnderProject(@NotNull DirectoryIndex directoryIndex, @NotNull VirtualFile f) {
    return ReadAction.compute(() -> {
      if (myProject.isDisposed()) throw new ProcessCanceledException();
      return directoryIndex.getInfoForFile(f).isInProject(f);
    });
  }

  public void mappingsChanged() {
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

  public void setDirectoryMappings(@NotNull List<? extends VcsDirectoryMapping> items) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("setDirectoryMappings, size: " + items.size(), new Throwable());
    }

    updateVcsMappings(items);
  }

  @Nullable
  public MappedRoot getMappedRootFor(@Nullable VirtualFile file) {
    if (file == null || !file.isInLocalFileSystem()) return null;
    if (myMappedRoots.isEmpty()) return null;
    if (myVcsManager.isIgnored(file)) return null;

    return myMappedRootsMapping.getRootFor(file);
  }

  @Nullable
  public MappedRoot getMappedRootFor(@Nullable FilePath file) {
    if (file == null || file.isNonLocal()) return null;
    if (myMappedRoots.isEmpty()) return null;
    if (myVcsManager.isIgnored(file)) return null;

    return myMappedRootsMapping.getRootFor(file);
  }

  @NotNull
  public List<VirtualFile> getMappingsAsFilesUnderVcs(@NotNull AbstractVcs vcs) {
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

  @Nullable
  public String haveDefaultMapping() {
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
          VcsBalloonProblemNotifier.showOverChangesView(myProject,
                                                        VcsBundle.message("impl.notification.content.vcs.plugin.not.found.for.mapping.to", vcsName),
                                                        MessageType.ERROR);
          filteredMappings.addAll(mappings);
        }
        else {
          filteredMappings.addAll(ContainerUtil.map(vcs.filterUniqueRoots(objects, pair -> pair.getFirst()), Functions.pairSecond()));
        }
      }
    }

    updateVcsMappings(filteredMappings);
  }

  @NotNull
  private MyVcsActivator createVcsActivator() {
    Set<AbstractVcs> newVcses = !TrustedProjects.isTrusted(myProject)
                                ? Collections.emptySet()
                                : ContainerUtil.map2SetNotNull(myMappings, mapping -> getMappingsVcs(mapping));

    List<AbstractVcs> oldVcses = myActiveVcses;
    myActiveVcses = Collections.unmodifiableList(new ArrayList<>(newVcses));

    refreshMainMenu();

    Collection<AbstractVcs> toAdd = ContainerUtil.subtract(myActiveVcses, oldVcses);
    Collection<AbstractVcs> toRemove = ContainerUtil.subtract(oldVcses, myActiveVcses);

    return new MyVcsActivator(toAdd, toRemove);
  }

  private static final class MyVcsActivator {
    @NotNull private final Collection<? extends AbstractVcs> myAddVcses;
    @NotNull private final Collection<? extends AbstractVcs> myRemoveVcses;

    private MyVcsActivator(@NotNull Collection<? extends AbstractVcs> addVcses,
                           @NotNull Collection<? extends AbstractVcs> removeVcses) {
      myAddVcses = addVcses;
      myRemoveVcses = removeVcses;
    }

    public void activate() {
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
    @Nullable public final AbstractVcs vcs;
    @NotNull public final VcsDirectoryMapping mapping;
    @NotNull public final VirtualFile root;

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
    @NotNull public final List<MappedRoot> mappedRoots;
    @NotNull public final Disposable filePointerDisposable;

    private Mappings(@NotNull List<MappedRoot> mappedRoots, @NotNull Disposable filePointerDisposable) {
      this.mappedRoots = mappedRoots;
      this.filePointerDisposable = filePointerDisposable;
    }
  }

  private static final class RootMapping {
    private final Map<VirtualFile, MappedRoot> myVFMap = new HashMap<>();
    private final FilePathMapping<MappedRoot> myPathMapping = new FilePathMapping<>(SystemInfo.isFileSystemCaseSensitive);

    private RootMapping(@NotNull List<MappedRoot> mappedRoots) {
      for (MappedRoot root : mappedRoots) {
        myVFMap.put(root.root, root);
        myPathMapping.add(root.root.getPath(), root);
      }
    }

    @Nullable
    public MappedRoot getRootFor(@NotNull VirtualFile file) {
      while (file != null) {
        MappedRoot root = myVFMap.get(file);
        if (root != null) return root;
        file = file.getParent();
      }
      return null;
    }

    @Nullable
    public MappedRoot getRootFor(@NotNull FilePath filePath) {
      return myPathMapping.getMappingFor(filePath.getPath());
    }
  }
}
