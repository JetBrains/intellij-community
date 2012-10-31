/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.*;
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

/**
 * @author max
 */
public class VirtualDirectoryImpl extends VirtualFileSystemEntry {
  private static final VirtualFileSystemEntry NULL_VIRTUAL_FILE = new VirtualFileImpl("*?;%NULL", null, -42, 0) {
    public String toString() {
      return "NULL";
    }
  };

  private final NewVirtualFileSystem myFS;
  private Object myChildren; // guarded by this, either Map<String, VFile> or VFile[]

  public VirtualDirectoryImpl(@NotNull final String name,
                              @Nullable final VirtualDirectoryImpl parent,
                              @NotNull final NewVirtualFileSystem fs,
                              final int id,
                              @PersistentFS.Attributes final int attributes) {
    super(name, parent, id, attributes);
    myFS = fs;
  }

  @Override
  @NotNull
  public NewVirtualFileSystem getFileSystem() {
    return myFS;
  }

  @Nullable
  private VirtualFileSystemEntry findChild(@NotNull String name,
                                           final boolean doRefresh,
                                           boolean ensureCanonicalName,
                                           @NotNull NewVirtualFileSystem delegate) {
    VirtualFileSystemEntry result = doFindChild(name, ensureCanonicalName, delegate);
    if (result == NULL_VIRTUAL_FILE) {
      return doRefresh ? createAndFindChildWithEventFire(name) : null;
    }

    if (result == null) {
      synchronized (this) {
        Map<String, VirtualFileSystemEntry> map = asMap();
        if (map != null) {
          map.put(name, NULL_VIRTUAL_FILE);
        }
      }
    }
    else if (doRefresh && delegate.isDirectory(result) != result.isDirectory()) {
      RefreshQueue.getInstance().refresh(false, false, null, result);
      result = doFindChild(name, ensureCanonicalName, delegate);
      if (result == NULL_VIRTUAL_FILE) {
        result = createAndFindChildWithEventFire(name);
      }
    }

    return result;
  }

  @Nullable
  private VirtualFileSystemEntry doFindChild(@NotNull String name,
                                             boolean ensureCanonicalName,
                                             @NotNull NewVirtualFileSystem delegate) {
    if (name.isEmpty()) {
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
      return NULL_VIRTUAL_FILE;
    }

    if (file != null) return file;

    if (ensureCanonicalName) {
      VirtualFile fake = new FakeVirtualFile(this, name);
      name = delegate.getCanonicallyCasedName(fake);
      if (name.isEmpty()) return null;
    }

    synchronized (this) {
      // do not extract getId outside the synchronized block since it will cause a concurrency problem.
      int id = ourPersistence.getId(this, name, delegate);
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
  public VirtualFileSystemEntry createChild(@NotNull String name, int id) {
    final VirtualFileSystemEntry child;
    final NewVirtualFileSystem fs = getFileSystem();

    final int attributes = ourPersistence.getFileAttributes(id);
    if (PersistentFS.isDirectory(attributes)) {
      child = new VirtualDirectoryImpl(name, this, fs, id, attributes);
    }
    else {
      child = new VirtualFileImpl(name, this, id, attributes);
      //noinspection TestOnlyProblems
      assertAccessInTests(child);
    }

    if (fs.markNewFilesAsDirty()) {
      child.markDirty();
    }

    return child;
  }


  private static final boolean IS_UNDER_TEAMCITY = System.getProperty("bootstrap.testcases") != null;
  private static final boolean SHOULD_PERFORM_ACCESS_CHECK = System.getenv("NO_FS_ROOTS_ACCESS_CHECK") == null;

  private static final Collection<String> ourAdditionalRoots = new THashSet<String>();

  @TestOnly
  public static void allowRootAccess(@NotNull String... roots) {
    for (String root : roots) {
      ourAdditionalRoots.add(FileUtil.toSystemIndependentName(root));
    }
  }

  @TestOnly
  public static void disallowRootAccess(@NotNull String... roots) {
    for (String root : roots) {
      ourAdditionalRoots.remove(FileUtil.toSystemIndependentName(root));
    }
  }

  @TestOnly
  private static void assertAccessInTests(VirtualFileSystemEntry child) {
    final Application application = ApplicationManager.getApplication();
    if (IS_UNDER_TEAMCITY &&
        SHOULD_PERFORM_ACCESS_CHECK &&
        application.isUnitTestMode() &&
        application instanceof ApplicationImpl &&
        ((ApplicationImpl)application).isComponentsCreated()) {
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
            assert local != null : child;
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
          assert false : "File accessed outside allowed roots: " + child + ";\nAllowed roots: " + new ArrayList<String>(allowed);
        }
      }
    }
  }

  // null means we were unable to get roots, so do not check access
  @Nullable
  private static Set<String> allowedRoots() {
    if (insideGettingRoots) return null;

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 0) return null;

    final Set<String> allowed = new THashSet<String>();
    allowed.add(FileUtil.toSystemIndependentName(PathManager.getHomePath()));

    try {
      URL outUrl = Application.class.getResource("/");
      String output = new File(outUrl.toURI()).getParentFile().getParentFile().getPath();
      allowed.add(FileUtil.toSystemIndependentName(output));
    }
    catch (URISyntaxException ignored) { }

    allowed.add(FileUtil.toSystemIndependentName(SystemProperties.getJavaHome()));
    allowed.add(FileUtil.toSystemIndependentName(new File(FileUtil.getTempDirectory()).getParent()));
    allowed.add(FileUtil.toSystemIndependentName(System.getProperty("java.io.tmpdir")));
    allowed.add(FileUtil.toSystemIndependentName(SystemProperties.getUserHome()));

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
      String location = project.getBasePath();
      assert location != null : project;
      allowed.add(FileUtil.toSystemIndependentName(location));
    }

    allowed.addAll(ourAdditionalRoots);

    return allowed;
  }

  private static boolean insideGettingRoots;

  private static VirtualFile[] getAllRoots(Project project) {
    insideGettingRoots = true;
    final Set<VirtualFile> roots = new THashSet<VirtualFile>();

    final OrderEnumerator enumerator = ProjectRootManager.getInstance(project).orderEntries();
    ContainerUtil.addAll(roots, enumerator.getClassesRoots());
    ContainerUtil.addAll(roots, enumerator.getSourceRoots());

    insideGettingRoots = false;
    return VfsUtilCore.toVirtualFileArray(roots);
  }

  @Nullable
  private VirtualFileSystemEntry createAndFindChildWithEventFire(@NotNull String name) {
    final NewVirtualFileSystem delegate = getFileSystem();
    final VirtualFile fake = new FakeVirtualFile(this, name);
    final FileAttributes attributes = delegate.getAttributes(fake);
    if (attributes != null) {
      final String realName = delegate.getCanonicallyCasedName(fake);
      final VFileCreateEvent event = new VFileCreateEvent(null, this, realName, attributes.isDirectory(), true);
      RefreshQueue.getInstance().processSingleEvent(event);
      return findChild(realName);
    }
    else {
      return null;
    }
  }

  @Override
  @Nullable
  public NewVirtualFile refreshAndFindChild(@NotNull String name) {
    return findChild(name, true, true, getFileSystem());
  }

  @Override
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
      @Override
      public boolean value(VirtualFile file) {
        return file != NULL_VIRTUAL_FILE;
      }
    });
  }

  @NotNull
  private synchronized Collection<VirtualFile> getInDbChildren() {
    VirtualFileSystemEntry[] children = asArray();
    if (children != null) {
      return Arrays.asList((VirtualFile[])children);
    }

    if (!ourPersistence.wereChildrenAccessed(this)) {
      return Collections.emptyList();
    }

    if (ourPersistence.areChildrenLoaded(this)) {
      return Arrays.asList(getChildren());
    }

    final String[] names = ourPersistence.listPersisted(this);
    final NewVirtualFileSystem delegate = PersistentFS.replaceWithNativeFS(getFileSystem());
    for (String name : names) {
      findChild(name, false, false, delegate);
    }

    // important: should return a copy here for safe iterations
    return new ArrayList<VirtualFile>(ensureAsMap().values());
  }

  @Override
  @NotNull
  public synchronized VirtualFile[] getChildren() {
    VirtualFileSystemEntry[] children = asArray();
    if (children != null) {
      return children;
    }

    Pair<String[], int[]> pair = ourPersistence.listAll(this);
    final int[] childrenIds = pair.second;
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

  @Override
  @Nullable
  public VirtualFileSystemEntry findChild(@NotNull final String name) {
    return findChild(name, false, true, getFileSystem());
  }

  @Override
  @Nullable
  public NewVirtualFile findChildById(int id) {
    final NewVirtualFile loaded = findChildByIdIfCached(id);
    if (loaded != null) {
      return loaded;
    }

    String name = ourPersistence.getName(id);
    return findChild(name, false, false, getFileSystem());
  }

  @Override
  public NewVirtualFile findChildByIdIfCached(int id) {
    final VirtualFile[] a;
    synchronized (this) {
      a = asArray();
    }
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

  @NotNull
  @Override
  public byte[] contentsToByteArray() throws IOException {
    throw new IOException("Cannot get content of directory: " + this);
  }

  // MUST BE CALLED UNDER this LOCK
  @Nullable
  private VirtualFileSystemEntry[] asArray() {
    Object children = myChildren;
    if (children instanceof VirtualFileSystemEntry[]) return (VirtualFileSystemEntry[])children;
    return null;
  }

  // MUST BE CALLED UNDER this LOCK
  @Nullable
  private Map<String, VirtualFileSystemEntry> asMap() {
    Object children = myChildren;
    if (children instanceof Map) {
      @SuppressWarnings({"unchecked"})
      final Map<String, VirtualFileSystemEntry> map = (Map<String, VirtualFileSystemEntry>)children;
      return map;
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
      @SuppressWarnings({"unchecked"})
      final Map<String, VirtualFileSystemEntry> aMap = (Map<String, VirtualFileSystemEntry>)myChildren;
      map = aMap;
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
    return asArray() != null;
  }

  @NotNull
  public synchronized List<String> getSuspiciousNames() {
    final Map<String, VirtualFileSystemEntry> map = asMap();
    if (map == null) return Collections.emptyList();

    List<String> names = null;

    for (Map.Entry<String, VirtualFileSystemEntry> entry : map.entrySet()) {
      if (entry.getValue() == NULL_VIRTUAL_FILE) {
        if (names == null) names = new SmartList<String>();
        names.add(entry.getKey());
      }
    }

    return names == null ? Collections.<String>emptyList() : names;
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
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
    assert ApplicationManager.getApplication().isUnitTestMode();
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

  @Override
  public InputStream getInputStream() throws IOException {
    throw new IOException("getInputStream() must not be called against a directory: " + getUrl());
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, final long newTimeStamp) throws IOException {
    throw new IOException("getOutputStream() must not be called against a directory: " + getUrl());
  }
}
