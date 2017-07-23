/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsInitObject;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Functions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.mapNotNull;
import static java.util.function.Function.identity;

public class NewMappings {

  public static final Comparator<VcsDirectoryMapping> MAPPINGS_COMPARATOR = Comparator.comparing(VcsDirectoryMapping::getDirectory);

  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.projectlevelman.NewMappings");
  private final Object myLock;

  // vcs to mappings
  private final MultiMap<String, VcsDirectoryMapping> myVcsToPaths;
  private AbstractVcs[] myActiveVcses;
  private VcsDirectoryMapping[] mySortedMappings;
  private FileWatchRequestsManager myFileWatchRequestsManager;

  private final DefaultVcsRootPolicy myDefaultVcsRootPolicy;
  private final ProjectLevelVcsManager myVcsManager;
  private final FileStatusManager myFileStatusManager;
  private final Project myProject;

  private boolean myActivated;

  public NewMappings(Project project,
                     ProjectLevelVcsManagerImpl vcsManager,
                     FileStatusManager fileStatusManager) {
    myProject = project;
    myVcsManager = vcsManager;
    myFileStatusManager = fileStatusManager;
    myLock = new Object();
    myVcsToPaths = MultiMap.createOrderedSet();
    myFileWatchRequestsManager = new FileWatchRequestsManager(myProject, this, LocalFileSystem.getInstance());
    myDefaultVcsRootPolicy = DefaultVcsRootPolicy.getInstance(project);
    myActiveVcses = new AbstractVcs[0];

    if (!myProject.isDefault()) {
      VcsDirectoryMapping mapping = new VcsDirectoryMapping("", "");
      myVcsToPaths.putValue("", mapping);
      mySortedMappings = new VcsDirectoryMapping[]{mapping};
    }
    else {
      mySortedMappings = VcsDirectoryMapping.EMPTY_ARRAY;
    }
    myActivated = false;

    vcsManager.addInitializationRequest(VcsInitObject.MAPPINGS, (DumbAwareRunnable)() -> {
      if (!myProject.isDisposed()) {
        activateActiveVcses();
      }
    });
  }

  // for tests
  public void setFileWatchRequestsManager(FileWatchRequestsManager fileWatchRequestsManager) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myFileWatchRequestsManager = fileWatchRequestsManager;
  }

  public AbstractVcs[] getActiveVcses() {
    synchronized (myLock) {
      final AbstractVcs[] result = new AbstractVcs[myActiveVcses.length];
      System.arraycopy(myActiveVcses, 0, result, 0, myActiveVcses.length);
      return result;
    }
  }

  public boolean hasActiveVcss() {
    synchronized (myLock) {
      return myActiveVcses.length > 0;
    }
  }

  public void activateActiveVcses() {
    synchronized (myLock) {
      if (myActivated) return;
      myActivated = true;
    }
    keepActiveVcs(EmptyRunnable.getInstance());
    mappingsChanged();
  }

  public void setMapping(final String path, final String activeVcsName) {
    LOG.debug("setMapping path = '" + path + "' vcs = " + activeVcsName);
    final VcsDirectoryMapping newMapping = new VcsDirectoryMapping(path, activeVcsName);
    // do not add duplicates
    synchronized (myLock) {
      Collection<VcsDirectoryMapping> vcsDirectoryMappings = myVcsToPaths.get(activeVcsName);
      if (vcsDirectoryMappings.contains(newMapping)) {
        return;
      }
    }

    final Ref<Boolean> switched = new Ref<>(Boolean.FALSE);
    keepActiveVcs(() -> {
      // sorted -> map. sorted mappings are NOT changed;
      switched.set(trySwitchVcs(path, activeVcsName));
      if (!switched.get().booleanValue()) {
        myVcsToPaths.putValue(newMapping.getVcs(), newMapping);
        sortedMappingsByMap();
      }
    });

    mappingsChanged();
  }

  private void keepActiveVcs(@NotNull Runnable runnable) {
    final MyVcsActivator activator;
    synchronized (myLock) {
      if (!myActivated) {
        runnable.run();
        return;
      }
      final HashSet<String> old = new HashSet<>();
      for (AbstractVcs activeVcs : myActiveVcses) {
        old.add(activeVcs.getName());
      }
      activator = new MyVcsActivator(old);
      runnable.run();
      restoreActiveVcses();
    }
    activator.activate(myVcsToPaths.keySet(), AllVcses.getInstance(myProject));
  }

  private void restoreActiveVcses() {
    synchronized (myLock) {
      final Set<String> set = myVcsToPaths.keySet();
      final List<AbstractVcs> list = new ArrayList<>(set.size());
      for (String s : set) {
        if (s.trim().length() == 0) continue;
        final AbstractVcs vcs = AllVcses.getInstance(myProject).getByName(s);
        if (vcs != null) {
          list.add(vcs);
        }
      }
      myActiveVcses = list.toArray(new AbstractVcs[list.size()]);
    }
  }

  public void mappingsChanged() {
    if (myProject.isDisposed()) return;
    myProject.getMessageBus().syncPublisher(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED).directoryMappingChanged();
    myFileStatusManager.fileStatusesChanged();
    myFileWatchRequestsManager.ping();

    for (VcsDirectoryMapping mapping : mySortedMappings) {
      String path = mapping.isDefaultMapping() ? "<Project>" : mapping.getDirectory();
      String vcs = mapping.getVcs();
      LOG.info(String.format("VCS Root: [%s] - [%s]", vcs, path));
    }
  }

  public void setDirectoryMappings(final List<VcsDirectoryMapping> items) {
    LOG.debug("setDirectoryMappings, size: " + items.size());

    final List<VcsDirectoryMapping> itemsCopy;
    if (items.isEmpty()) {
      itemsCopy = Collections.singletonList(new VcsDirectoryMapping("", ""));
    }
    else {
      itemsCopy = items;
    }

    keepActiveVcs(() -> {
      myVcsToPaths.clear();
      for (VcsDirectoryMapping mapping : itemsCopy) {
        myVcsToPaths.putValue(mapping.getVcs(), mapping);
      }
      sortedMappingsByMap();
    });

    mappingsChanged();
  }

  @Nullable
  public VcsDirectoryMapping getMappingFor(@Nullable VirtualFile file) {
    if (file == null) return null;
    if (!file.isInLocalFileSystem()) {
      return null;
    }

    return getMappingFor(file, myDefaultVcsRootPolicy.getMatchContext(file));
  }

  @Nullable
  public VcsDirectoryMapping getMappingFor(final VirtualFile file, final Object parentModule) {
    // if parentModule is not null it means that file belongs to the module so it isn't excluded
    if (parentModule == null && myVcsManager.isIgnored(file)) {
      return null;
    }

    // performance: calculate file path just once, rather than once per mapping
    String path = file.getPath();
    final String systemIndependentPath = FileUtil.toSystemIndependentName((file.isDirectory() && (!path.endsWith("/"))) ? (path + "/") : path);
    final VcsDirectoryMapping[] mappings;
    synchronized (myLock) {
      mappings = mySortedMappings;
    }
    for (int i = mappings.length - 1; i >= 0; --i) {
      final VcsDirectoryMapping mapping = mappings[i];
      if (fileMatchesMapping(file, parentModule, systemIndependentPath, mapping)) {
        return mapping;
      }
    }
    return null;
  }

  @Nullable
  public String getVcsFor(@NotNull VirtualFile file) {
    VcsDirectoryMapping mapping = getMappingFor(file);
    if (mapping == null) {
      return null;
    }
    return mapping.getVcs();
  }

  private boolean fileMatchesMapping(@NotNull VirtualFile file,
                                     final Object matchContext,
                                     final String systemIndependentPath,
                                     final VcsDirectoryMapping mapping) {
    if (mapping.getDirectory().length() == 0) {
      return myDefaultVcsRootPolicy.matchesDefaultMapping(file, matchContext);
    }
    return FileUtil.startsWith(systemIndependentPath, mapping.systemIndependentPath());
  }

  @NotNull
  public List<VirtualFile> getMappingsAsFilesUnderVcs(@NotNull AbstractVcs vcs) {
    final List<VirtualFile> result = new ArrayList<>();
    final String vcsName = vcs.getName();

    final List<VcsDirectoryMapping> mappings;
    synchronized (myLock) {
      final Collection<VcsDirectoryMapping> vcsMappings = myVcsToPaths.get(vcsName);
      if (vcsMappings.isEmpty()) return result;
      mappings = new ArrayList<>(vcsMappings);
    }

    for (VcsDirectoryMapping mapping : mappings) {
      if (mapping.isDefaultMapping()) {
        result.addAll(myDefaultVcsRootPolicy.getDefaultVcsRoots(this, vcsName));
      }
      else {
        final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(mapping.getDirectory());
        if (file != null) {
          result.add(file);
        }
      }
    }
    result.removeIf(file -> !file.isDirectory());
    return result;
  }

  public void disposeMe() {
    LOG.debug("dispose me");
    clearImpl();
  }

  public void clear() {
    LOG.debug("clear");
    clearImpl();

    mappingsChanged();
  }

  private void clearImpl() {
    // if vcses were not mapped, there's nothing to clear
    if ((myActiveVcses == null) || (myActiveVcses.length == 0)) return;

    keepActiveVcs(() -> {
      myVcsToPaths.clear();
      myActiveVcses = new AbstractVcs[0];
      mySortedMappings = VcsDirectoryMapping.EMPTY_ARRAY;
    });
    myFileWatchRequestsManager.ping();
  }

  public List<VcsDirectoryMapping> getDirectoryMappings() {
    synchronized (myLock) {
      return Arrays.asList(mySortedMappings);
    }
  }

  public List<VcsDirectoryMapping> getDirectoryMappings(String vcsName) {
    synchronized (myLock) {
      Collection<VcsDirectoryMapping> mappings = myVcsToPaths.get(vcsName);
      return mappings.isEmpty() ? new ArrayList<>() : new ArrayList<>(mappings);
    }
  }

  public void cleanupMappings() {
    synchronized (myLock) {
      removeRedundantMappings();
    }
    myFileWatchRequestsManager.ping();
  }

  @Nullable
  public String haveDefaultMapping() {
    synchronized (myLock) {
      // empty mapping MUST be first
      if (mySortedMappings.length == 0) return null;
      return mySortedMappings[0].isDefaultMapping() ? mySortedMappings[0].getVcs() : null;
    }
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return mySortedMappings.length == 0 || ContainerUtil.and(mySortedMappings, mapping -> mapping.getVcs().isEmpty());
    }
  }

  public void removeDirectoryMapping(final VcsDirectoryMapping mapping) {
    LOG.debug("remove mapping: " + mapping.getDirectory());

    keepActiveVcs(() -> {
      if (removeVcsFromMap(mapping, mapping.getVcs())) {
        sortedMappingsByMap();
      }
    });

    mappingsChanged();
  }

  // todo area for optimization
  private void removeRedundantMappings() {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final AllVcsesI allVcses = AllVcses.getInstance(myProject);

    for (Iterator<String> iterator = myVcsToPaths.keySet().iterator(); iterator.hasNext(); ) {
      final String vcsName = iterator.next();
      final Collection<VcsDirectoryMapping> mappings = myVcsToPaths.get(vcsName);

      List<Pair<VirtualFile, VcsDirectoryMapping>> objects = mapNotNull(mappings, dm -> {
        VirtualFile vf = lfs.findFileByPath(dm.getDirectory());
        if (vf == null) {
          vf = lfs.refreshAndFindFileByPath(dm.getDirectory());
        }
        return vf == null ? null : Pair.create(vf, dm);
      });

      final List<Pair<VirtualFile, VcsDirectoryMapping>> filteredFiles;
      // todo static
      Function<Pair<VirtualFile, VcsDirectoryMapping>, VirtualFile> fileConvertor = pair -> pair.getFirst();
      if (StringUtil.isEmptyOrSpaces(vcsName)) {
        filteredFiles = AbstractVcs.filterUniqueRootsDefault(objects, fileConvertor);
      }
      else {
        final AbstractVcs<?> vcs = allVcses.getByName(vcsName);
        if (vcs == null) {
          VcsBalloonProblemNotifier.showOverChangesView(myProject, "VCS plugin not found for mapping to : '" + vcsName + "'", MessageType.ERROR);
          continue;
        }
        filteredFiles = vcs.filterUniqueRoots(objects, fileConvertor);
      }

      List<VcsDirectoryMapping> filteredMappings = map(filteredFiles, Functions.pairSecond());
      // to calculate what had been removed
      mappings.removeAll(filteredMappings);

      if (filteredMappings.isEmpty()) {
        iterator.remove();
      }
      else {
        mappings.clear();
        mappings.addAll(filteredMappings);
      }
    }

    sortedMappingsByMap();
  }

  private boolean trySwitchVcs(final String path, final String activeVcsName) {
    final String fixedPath = FileUtil.toSystemIndependentName(path);
    for (VcsDirectoryMapping mapping : mySortedMappings) {
      if (mapping.systemIndependentPath().equals(fixedPath)) {
        final String oldVcs = mapping.getVcs();
        if (!oldVcs.equals(activeVcsName)) {
          migrateVcs(activeVcsName, mapping, oldVcs);
        }
        return true;
      }
    }
    return false;
  }

  private void sortedMappingsByMap() {
    mySortedMappings = ArrayUtil.toObjectArray(myVcsToPaths.values(), VcsDirectoryMapping.class);
    Arrays.sort(mySortedMappings, MAPPINGS_COMPARATOR);
  }

  private void migrateVcs(String activeVcsName, VcsDirectoryMapping mapping, String oldVcs) {
    mapping.setVcs(activeVcsName);

    removeVcsFromMap(mapping, oldVcs);

    myVcsToPaths.putValue(activeVcsName, mapping);
  }

  private boolean removeVcsFromMap(VcsDirectoryMapping mapping, String oldVcs) {
    return myVcsToPaths.remove(oldVcs, mapping);
  }

  private static class MyVcsActivator {
    private final Set<String> myOld;

    public MyVcsActivator(final Set<String> old) {
      myOld = old;
    }

    public void activate(final Set<String> newOne, final AllVcsesI vcsesI) {
      final Set<String> toAdd = notInBottom(newOne, myOld);
      final Set<String> toRemove = notInBottom(myOld, newOne);
      if (toAdd != null) {
        for (String s : toAdd) {
          final AbstractVcs vcs = vcsesI.getByName(s);
          if (vcs != null) {
            try {
              vcs.doActivate();
            }
            catch (VcsException e) {
              // actually is not thrown (AbstractVcs#actualActivate())
            }
          }
          else {
            LOG.info("Error: activating non existing vcs: " + s);
          }
        }
      }
      if (toRemove != null) {
        for (String s : toRemove) {
          final AbstractVcs vcs = vcsesI.getByName(s);
          if (vcs != null) {
            try {
              vcs.doDeactivate();
            }
            catch (VcsException e) {
              // actually is not thrown (AbstractVcs#actualDeactivate())
            }
          }
          else {
            LOG.info("Error: removing non existing vcs: " + s);
          }
        }
      }
    }

    @Nullable
    private static Set<String> notInBottom(final Set<String> top, final Set<String> bottom) {
      Set<String> notInBottom = null;
      for (String topItem : top) {
        // omit empty vcs: not a vcs
        if (topItem.trim().length() == 0) continue;

        if (!bottom.contains(topItem)) {
          if (notInBottom == null) {
            notInBottom = new HashSet<>();
          }
          notInBottom.add(topItem);
        }
      }
      return notInBottom;
    }
  }

  public boolean haveActiveVcs(final String name) {
    synchronized (myLock) {
      return myVcsToPaths.containsKey(name);
    }
  }

  public void beingUnregistered(final String name) {
    synchronized (myLock) {
      keepActiveVcs(() -> {
        myVcsToPaths.remove(name);
        sortedMappingsByMap();
      });
    }

    mappingsChanged();
  }

  @NotNull
  public List<VirtualFile> getDefaultRoots() {
    synchronized (myLock) {
      final String defaultVcs = haveDefaultMapping();
      if (defaultVcs == null) return Collections.emptyList();
      final List<VirtualFile> list = new ArrayList<>();
      list.addAll(myDefaultVcsRootPolicy.getDefaultVcsRoots(this, defaultVcs));
      if (StringUtil.isEmptyOrSpaces(defaultVcs)) {
        return AbstractVcs.filterUniqueRootsDefault(list, identity());
      }
      else {
        final AbstractVcs<?> vcs = AllVcses.getInstance(myProject).getByName(defaultVcs);
        if (vcs == null) {
          return AbstractVcs.filterUniqueRootsDefault(list, identity());
        }
        return vcs.filterUniqueRoots(list, identity());
      }
    }
  }
}
