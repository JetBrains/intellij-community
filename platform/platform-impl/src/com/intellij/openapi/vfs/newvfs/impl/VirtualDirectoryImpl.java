/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class VirtualDirectoryImpl extends VirtualFileSystemEntry {
  private static final VirtualFileSystemEntry NULL_VIRTUAL_FILE = new VirtualFileImpl("*?;%NULL", null, -42);
  private final NewVirtualFileSystem myFS;

  // guarded by this
  private Object myChildren; // Either HashMap<String, VFile> or VFile[]

  public VirtualDirectoryImpl(@NotNull String name, final VirtualDirectoryImpl parent, @NotNull NewVirtualFileSystem fs, final int id) {
    super(name, parent, id);
    myFS = fs;
  }

  @NotNull
  public NewVirtualFileSystem getFileSystem() {
    return myFS;
  }

  @Nullable
  private VirtualFileSystemEntry findChild(@NotNull String name, final boolean createIfNotFound, boolean ensureCanonicalName) {
    final VirtualFileSystemEntry result = doFindChild(name, createIfNotFound, ensureCanonicalName);
    if (result == NULL_VIRTUAL_FILE) {
      return createIfNotFound ? createAndFindChildWithEventFire(name) : null;
    }

    if (result == null) {
      synchronized (this) {
        Map<String, VirtualFileSystemEntry> map = asMap();
        if (map != null) {
          map.put(name, NULL_VIRTUAL_FILE);
        }
      }
    }

    return result;
  }

  @Nullable
  private VirtualFileSystemEntry doFindChild(@NotNull String name, final boolean createIfNotFound, boolean ensureCanonicalName) {
    if (name.length() == 0) {
      return null;
    }

    final VirtualFileSystemEntry[] array;
    final Map<String, VirtualFileSystemEntry> map;
    final VirtualFileSystemEntry file;
    synchronized (this) {
      array = asArray();
      if (array == null) {
        map = ensureAsMap();
        file = map.get(name);
      }
      else {
        file = null;
        map = null;
      }
    }
    if (array != null) {
      final boolean ignoreCase = !getFileSystem().isCaseSensitive();
      for (VirtualFileSystemEntry vf : array) {
        if (vf.nameMatches(name, ignoreCase)) return vf;
      }
      return createIfNotFound ? createAndFindChildWithEventFire(name) : null;
    }

    if (file != null) return file;

    if (ensureCanonicalName) {
      final NewVirtualFileSystem delegate = getFileSystem();
      VirtualFile fake = new FakeVirtualFile(this, name);
      name = delegate.getCanonicallyCasedName(fake);
      if (name.length() == 0) return null;
    }

    synchronized (this) {
      // do not extract getId outside the synchronized block since it will cause a concurrency problem.
      int id = PersistentFS.getId(this, name);
      if (id > 0) {
        // maybe another doFindChild() sneaked in the middle
        VirtualFileSystemEntry lastTry = map.get(name);
        if (lastTry != null) return lastTry;

        final String shorty = new String(name);
        VirtualFileSystemEntry child = createChild(shorty, id); // So we don't hold whole char[] buffer of a lengthy path
        map.put(shorty, child);
        return child;
      }
    }

    return null;
  }

  @NotNull
  public VirtualFileSystemEntry createChild(String name, int id) {
    final VirtualFileSystemEntry child;
    final NewVirtualFileSystem fs = getFileSystem();
    if (PersistentFS.isDirectory(id)) {
      child = new VirtualDirectoryImpl(name, this, fs, id);
    }
    else {
      child = new VirtualFileImpl(name, this, id);
      assertAccessInTests(child);
    }

    if (fs.markNewFilesAsDirty()) {
      child.markDirty();
    }

    return child;
  }


  private static final boolean IS_UNDER_TEAMCITY = System.getProperty("bootstrap.testcases") != null;
  private static final boolean IS_UNIT_TESTS = ApplicationManager.getApplication().isUnitTestMode();
  private static final Collection<String> additionalRoots = new THashSet<String>();
  @TestOnly
  public static void allowToAccess(@NotNull String root) { additionalRoots.add(FileUtil.toSystemIndependentName(root)); }
  @TestOnly
  private static void assertAccessInTests(VirtualFileSystemEntry child) {
    if (IS_UNIT_TESTS && IS_UNDER_TEAMCITY && ApplicationManager.getApplication() instanceof ApplicationImpl && ((ApplicationImpl)ApplicationManager.getApplication()).isComponentsCreated()) {
      NewVirtualFileSystem fileSystem = child.getFileSystem();
      if (fileSystem != LocalFileSystem.getInstance() && fileSystem != JarFileSystem.getInstance()) {
        return;
      }
      // root' children are loaded always
      if (child.getParent() == null || child.getParent().getParent() == null) return;

      Set<String> allowed = allowedRoots();
      boolean isUnder = allowed == null;
      if (!isUnder) {
        for (String root : allowed) {
          String childPath = child.getPath();
          if (child.getFileSystem() == JarFileSystem.getInstance()) {
            VirtualFile local = JarFileSystem.getInstance().getVirtualFileForJar(child);
            childPath = local.getPath();
          }
          if (FileUtil.startsWith(childPath, root)) {
            isUnder = true;
            break;
          }
          if (root.startsWith(JarFileSystem.PROTOCOL_PREFIX)) {
            String rootLocalPath = FileUtil.toSystemIndependentName(PathUtil.toPresentableUrl(root));
            isUnder = FileUtil.startsWith(childPath, rootLocalPath);
            if (isUnder) break;
          }
        }
      }

      if (!isUnder) {
        if (!allowed.isEmpty()) {
          assert false : "File accessed outside allowed roots: " + child +";\n Allowed roots: "+new ArrayList(allowed);
        }
      }
    }
  }

  // null means we were unable to get roots, so do not check access
  private static Set<String> allowedRoots() {
    if (insideGettingRoots) return null;
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 0) return null;
    final Set<String> allowed = new THashSet<String>();
    String homePath = PathManager.getHomePath();
    allowed.add(FileUtil.toSystemIndependentName(homePath));
    try {
      URL outUrl = Application.class.getResource("/");
      String output = new File(outUrl.toURI()).getParentFile().getParentFile().getPath();
      allowed.add(FileUtil.toSystemIndependentName(output));
    }
    catch (URISyntaxException ignored) {
    }
    String javaHome = SystemProperties.getJavaHome();
    allowed.add(FileUtil.toSystemIndependentName(javaHome));
    String tempDirectorySpecific = new File(FileUtil.getTempDirectory()).getParent();
    allowed.add(FileUtil.toSystemIndependentName(tempDirectorySpecific));
    String tempDirectory = System.getProperty("java.io.tmpdir");
    allowed.add(FileUtil.toSystemIndependentName(tempDirectory));
    String userHome = SystemProperties.getUserHome();
    allowed.add(FileUtil.toSystemIndependentName(userHome));
    for (Project project : openProjects) {
      if (!project.isInitialized()) {
        return null; // all is allowed
      }
      for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
        allowed.add(root.getPath());
      }
      for (VirtualFile root : getAllRoots(project)) {
        allowed.add(StringUtil.trimEnd(root.getPath(), JarFileSystem.JAR_SEPARATOR));
      }
      String location = project.getLocation();
      allowed.add(FileUtil.toSystemIndependentName(location));
    }

    //for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
    //  allowed.add(FileUtil.toSystemIndependentName(sdk.getHomePath()));
    //}
    for (String root : additionalRoots) {
      allowed.add(root);
    }
    return allowed;
  }

  private static boolean insideGettingRoots;
  private static VirtualFile[] getAllRoots(Project project) {
    insideGettingRoots = true;
    Set<VirtualFile> roots = new THashSet<VirtualFile>();
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (OrderEntry entry : orderEntries) {
        VirtualFile[] files;
        files = entry.getFiles(OrderRootType.CLASSES);
        ContainerUtil.addAll(roots, files);
        files = entry.getFiles(OrderRootType.SOURCES);
        ContainerUtil.addAll(roots, files);
        files = entry.getFiles(OrderRootType.CLASSES_AND_OUTPUT);
        ContainerUtil.addAll(roots, files);
      }
    }
    insideGettingRoots = false;
    return VfsUtil.toVirtualFileArray(roots);
  }


  @Nullable
  private VirtualFileSystemEntry createAndFindChildWithEventFire(@NotNull String name) {
    final NewVirtualFileSystem delegate = getFileSystem();
    VirtualFile fake = new FakeVirtualFile(this, name);
    if (delegate.exists(fake)) {
      final String realName = delegate.getCanonicallyCasedName(fake);
      VFileCreateEvent event = new VFileCreateEvent(null, this, realName, delegate.isDirectory(fake), true);
      RefreshQueue.getInstance().processSingleEvent(event);
      return findChild(realName);
    }
    else {
      return null;
    }
  }

  @Nullable
  public NewVirtualFile refreshAndFindChild(@NotNull String name) {
    return findChild(name, true, true);
  }

  @Nullable
  public synchronized NewVirtualFile findChildIfCached(@NotNull String name) {
    final VirtualFileSystemEntry[] a = asArray();
    if (a != null) {
      final boolean ignoreCase = !getFileSystem().isCaseSensitive();
      for (VirtualFileSystemEntry file : a) {
        if (file.nameMatches(name, ignoreCase)) return file;
      }

      return null;
    }

    final Map<String, VirtualFileSystemEntry> map = asMap();
    if (map != null) {
      final VirtualFileSystemEntry file = map.get(name);
      return file != NULL_VIRTUAL_FILE ? file : null;
    }

    return null;
  }

  @Override
  @NotNull
  public Iterable<VirtualFile> iterInDbChildren() {
    return ContainerUtil.iterate(getInDbChildren(), new Condition<VirtualFile>() {
      public boolean value(VirtualFile file) {
        return file != NULL_VIRTUAL_FILE;
      }
    });
  }

  @NotNull
  private synchronized Collection<VirtualFile> getInDbChildren() {
    if (myChildren instanceof VirtualFileSystemEntry[]) {
      return Arrays.asList((VirtualFile[])myChildren);
    }

    if (!ourPersistence.wereChildrenAccessed(this)) {
      return Collections.emptyList();
    }

    if (ourPersistence.areChildrenLoaded(this)) {
      return Arrays.asList(getChildren());
    }

    final String[] names = PersistentFS.listPersisted(this);
    for (String name : names) {
      findChild(name, false, false);
    }
    
    // important: should return a copy here for safe iterations
    return new ArrayList<VirtualFile>(ensureAsMap().values());
  }

  @NotNull
  public synchronized VirtualFile[] getChildren() {
    if (myChildren instanceof VirtualFileSystemEntry[]) {
      return (VirtualFileSystemEntry[])myChildren;
    }

    Pair<String[],int[]> pair = PersistentFS.listAll(this);
    final int[] childrenIds = pair.second;
    VirtualFileSystemEntry[] children;
    if (childrenIds.length == 0) {
      children = EMPTY_ARRAY;
    }
    else {
      children = new VirtualFileSystemEntry[childrenIds.length];
      String[] names = pair.first;
      final Map<String, VirtualFileSystemEntry> map = asMap();
      for (int i = 0; i < children.length; i++) {
        final int childId = childrenIds[i];
        final String name = names[i];
        VirtualFileSystemEntry child = map != null ? map.get(name) : null;

        children[i] = child != null && child != NULL_VIRTUAL_FILE ? child : createChild(name, childId);
      }
    }

    if (getId() > 0) {
      myChildren = children;
    }

    return children;
  }

  @Nullable
  public VirtualFileSystemEntry findChild(@NotNull final String name) {
    return findChild(name, false, true);
  }

  @Nullable
  public NewVirtualFile findChildById(int id) {
    final NewVirtualFile loaded = findChildByIdIfCached(id);
    if (loaded != null) {
      return loaded;
    }
    
    String name = ourPersistence.getName(id);
    return findChild(name, false, false);
  }

  public NewVirtualFile findChildByIdIfCached(int id) {
    final VirtualFile[] a = asArray();
    if (a != null) {
      for (VirtualFile file : a) {
        NewVirtualFile withId = (NewVirtualFile)file;
        if (withId.getId() == id) return withId;
      }

      return null;
    }
    synchronized (this) {
      final Map<String, VirtualFileSystemEntry> map = asMap();
      if (map != null) {
        for (Map.Entry<String, VirtualFileSystemEntry> entry : map.entrySet()) {
          VirtualFile file = entry.getValue();
          if (file == NULL_VIRTUAL_FILE) continue;
          NewVirtualFile withId = (NewVirtualFile)file;
          if (withId.getId() == id) return withId;
        }
      }
    }
    return null;
  }

  @Nullable
  private VirtualFileSystemEntry[] asArray() {
    if (myChildren instanceof VirtualFileSystemEntry[]) return (VirtualFileSystemEntry[])myChildren;
    return null;
  }

  @Nullable
  private Map<String, VirtualFileSystemEntry> asMap() {
    if (myChildren instanceof Map) {
      //noinspection unchecked
      return (Map<String, VirtualFileSystemEntry>)myChildren;
    }
    return null;
  }

  @NotNull
  private Map<String, VirtualFileSystemEntry> ensureAsMap() {
    Map<String, VirtualFileSystemEntry> map;
    if (myChildren == null) {
      map = createMap();
      myChildren = map;
    }
    else {
      //noinspection unchecked
      map = (Map<String, VirtualFileSystemEntry>)myChildren;
    }

    return map;
  }

  public synchronized void addChild(@NotNull VirtualFileSystemEntry file) {
    final VirtualFileSystemEntry[] a = asArray();
    if (a != null) {
      myChildren = ArrayUtil.append(a, file);
    }
    else {
      ensureAsMap().put(file.getName(), file);
    }
  }

  public synchronized void removeChild(@NotNull VirtualFile file) {
    final VirtualFileSystemEntry[] a = asArray();
    if (a != null) {
      myChildren = ArrayUtil.remove(a, file);
    }
    else {
      ensureAsMap().put(file.getName(), NULL_VIRTUAL_FILE);
    }
  }

  public synchronized boolean allChildrenLoaded() {
    return myChildren instanceof VirtualFileSystemEntry[];
  }

  @NotNull
  public synchronized List<String> getSuspiciousNames() {
    final Map<String, VirtualFileSystemEntry> map = asMap();
    if (map == null) return Collections.emptyList();

    List<String> names = new ArrayList<String>();
    for (Map.Entry<String, VirtualFileSystemEntry> entry : map.entrySet()) {
      if (entry.getValue() == NULL_VIRTUAL_FILE) {
        names.add(entry.getKey());
      }
    }

    return names;
  }

  public boolean isDirectory() {
    return true;
  }

  @NotNull
  public synchronized Collection<VirtualFile> getCachedChildren() {
    final Map<String, VirtualFileSystemEntry> map = asMap();
    if (map != null) {
      Set<VirtualFile> files = new THashSet<VirtualFile>(map.values());
      files.remove(NULL_VIRTUAL_FILE);
      return files;
    }

    final VirtualFile[] a = asArray();
    if (a != null) return Arrays.asList(a);

    return Collections.emptyList();
  }

  @NotNull
  private Map<String, VirtualFileSystemEntry> createMap() {
    return getFileSystem().isCaseSensitive()
           ? new THashMap<String, VirtualFileSystemEntry>()
           : new THashMap<String, VirtualFileSystemEntry>(CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  @TestOnly
  public synchronized void cleanupCachedChildren(@NotNull Set<VirtualFile> survivors) {
    if (survivors.contains(this)) {
      for (VirtualFile file : getCachedChildren()) {
        if (file instanceof VirtualDirectoryImpl) {
          ((VirtualDirectoryImpl)file).cleanupCachedChildren(survivors);
        }
      }
    }
    else {
      myChildren = null;
    }
  }

  public InputStream getInputStream() throws IOException {
    throw new IOException("getInputStream() must not be called against a directory: " + getUrl());
  }

  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, final long newTimeStamp) throws IOException {
    throw new IOException("getOutputStream() must not be called against a directory: " + getUrl());
  }
}
