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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class VirtualDirectoryImpl extends VirtualFileSystemEntry {
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
  private NewVirtualFile findChild(@NotNull String name, final boolean createIfNotFound, boolean ensureCanonicalName) {
    final VirtualFile result = doFindChild(name, createIfNotFound, ensureCanonicalName);
    if (result == NullVirtualFile.INSTANCE) {
      return createIfNotFound ? createAndFindChildWithEventFire(name) : null;
    }

    if (result == null) {
      synchronized (this) {
        Map<String, VirtualFile> map = asMap();
        if (map != null) {
          map.put(name, NullVirtualFile.INSTANCE);
        }
      }
    }

    return (NewVirtualFile)result;
  }

  @Nullable
  private VirtualFile doFindChild(@NotNull String name, final boolean createIfNotFound, boolean ensureCanonicalName) {
    if (name.length() == 0) {
      return null;
    }

    final VirtualFile[] a;
    synchronized (this) {
      a = asArray();
    }
    if (a != null) {
      final boolean ignoreCase = !getFileSystem().isCaseSensitive();
      for (VirtualFile vf : a) {
        if (namesEqual(name, ignoreCase, vf)) return vf;
      }

      return createIfNotFound ? createAndFindChildWithEventFire(name) : null;
    }

    final Map<String, VirtualFile> map;
    final VirtualFile file;
    synchronized (this) {
      map = ensureAsMap();
      file = map.get(name);
    }

    if (file != null) return file;

    if (ensureCanonicalName) {
      final NewVirtualFileSystem delegate = getFileSystem();
      VirtualFile fake = new FakeVirtualFile(this, name);
      name = delegate.getCanonicallyCasedName(fake);
      if (name.length() == 0) return null;
    }

    synchronized (this) {
      // do not extract getId from under the synchronized block since it will cause a concurrency problem.
      int id = ourPersistence.getId(this, name);
      if (id > 0) {
        final String shorty = new String(name);
        NewVirtualFile child = createChild(shorty, id); // So we don't hold whole char[] buffer of a lengthy path
        map.put(shorty, child);
        return child;
      }
    }

    return null;
  }

  private static boolean namesEqual(String name, boolean ignoreCase, VirtualFile file) {
    if (file instanceof VirtualFileSystemEntry) {
      return (((VirtualFileSystemEntry)file).nameMatches(name, ignoreCase));
    }

    final String name2 = file.getName();
    if (ignoreCase) {
      return name.equalsIgnoreCase(name2);
    }
    return name.equals(name2);
  }

  @NotNull
  public VirtualFileSystemEntry createChild(String name, int id) {
    final VirtualFileSystemEntry child;
    final NewVirtualFileSystem fs = getFileSystem();
    if (ourPersistence.isDirectory(id)) {
      child = new VirtualDirectoryImpl(name, this, fs, id);
    }
    else {
      child = new VirtualFileImpl(name, this, id);
    }

    if (fs.markNewFilesAsDirty()) {
      child.markDirty();
    }

    return child;
  }

  @Nullable
  private NewVirtualFile createAndFindChildWithEventFire(@NotNull String name) {
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
    final VirtualFile[] a = asArray();
    if (a != null) {
      final boolean ignoreCase = !getFileSystem().isCaseSensitive();
      for (VirtualFile file : a) {
        if (namesEqual(name, ignoreCase, file)) return (NewVirtualFile)file;
      }

      return null;
    }

    final Map<String, VirtualFile> map = asMap();
    if (map != null) {
      final VirtualFile file = map.get(name);
      return file instanceof NewVirtualFile ? (NewVirtualFile)file : null;
    }

    return null;
  }

  @Override
  @NotNull
  public Iterable<VirtualFile> iterInDbChildren() {
    return ContainerUtil.iterate(getInDbChildren(), new Condition<VirtualFile>() {
      public boolean value(VirtualFile file) {
        return file != NullVirtualFile.INSTANCE;
      }
    });
  }

  @NotNull
  private synchronized Collection<VirtualFile> getInDbChildren() {
    if (myChildren instanceof VirtualFile[]) {
      return Arrays.asList((VirtualFile[])myChildren);
    }

    if (!ourPersistence.wereChildrenAccessed(this)) {
      return Collections.emptyList();
    }

    if (ourPersistence.areChildrenLoaded(this)) {
      return Arrays.asList(getChildren());
    }

    final String[] names = ourPersistence.listPersisted(this);
    for (String name : names) {
      findChild(name, false, false);
    }
    
    // important: should return a copy here for safe iterations
    return new ArrayList<VirtualFile>(ensureAsMap().values());
  }

  @NotNull
  public synchronized VirtualFile[] getChildren() {
    if (myChildren instanceof VirtualFile[]) {
      return (VirtualFile[])myChildren;
    }

    Pair<String[],int[]> pair = PersistentFS.listAll(this);
    final int[] childrenIds = pair.second;
    VirtualFile[] children;
    if (childrenIds.length == 0) {
      children = EMPTY_ARRAY;
    }
    else {
      children = new VirtualFile[childrenIds.length];
      String[] names = pair.first;
      final Map<String, VirtualFile> map = asMap();
      for (int i = 0; i < children.length; i++) {
        final int childId = childrenIds[i];
        final String name = names[i];
        VirtualFile child = map != null ? map.get(name) : null;

        children[i] = child != null && child != NullVirtualFile.INSTANCE ? child : createChild(name, childId);
      }
    }

    if (getId() > 0) {
      myChildren = children;
    }

    return children;
  }

  @Nullable
  public NewVirtualFile findChild(@NotNull final String name) {
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
      final Map<String, VirtualFile> map = asMap();
      if (map != null) {
        for (Map.Entry<String, VirtualFile> entry : map.entrySet()) {
          VirtualFile file = entry.getValue();
          if (file == NullVirtualFile.INSTANCE) continue;
          NewVirtualFile withId = (NewVirtualFile)file;
          if (withId.getId() == id) return withId;
        }
      }
    }
    return null;
  }

  @Nullable
  private VirtualFile[] asArray() {
    if (myChildren instanceof VirtualFile[]) return (VirtualFile[])myChildren;
    return null;
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  private Map<String, VirtualFile> asMap() {
    if (myChildren instanceof Map) return (Map<String, VirtualFile>)myChildren;
    return null;
  }

  @NotNull
  @SuppressWarnings({"unchecked"})
  private Map<String, VirtualFile> ensureAsMap() {
    Map<String, VirtualFile> map;
    if (myChildren == null) {
      map = createMap();
      myChildren = map;
    }
    else {
      map = (Map<String, VirtualFile>)myChildren;
    }

    return map;
  }

  public synchronized void addChild(@NotNull VirtualFile file) {
    final VirtualFile[] a = asArray();
    if (a != null) {
      myChildren = ArrayUtil.append(a, file);
    }
    else {
      final Map<String, VirtualFile> m = ensureAsMap();
      m.put(file.getName(), file);
    }
  }

  public synchronized void removeChild(@NotNull VirtualFile file) {
    final VirtualFile[] a = asArray();
    if (a != null) {
      myChildren = ArrayUtil.remove(a, file);
    }
    else {
      final Map<String, VirtualFile> m = ensureAsMap();
      m.put(file.getName(), NullVirtualFile.INSTANCE);
    }
  }

  public synchronized boolean allChildrenLoaded() {
    return myChildren instanceof VirtualFile[];
  }

  @NotNull
  public synchronized List<String> getSuspiciousNames() {
    final Map<String, VirtualFile> map = asMap();
    if (map == null) return Collections.emptyList();

    List<String> names = new ArrayList<String>();
    for (Map.Entry<String, VirtualFile> entry : map.entrySet()) {
      if (entry.getValue() == NullVirtualFile.INSTANCE) {
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
    final Map<String, VirtualFile> map = asMap();
    if (map != null) {
      Set<VirtualFile> files = new THashSet<VirtualFile>(map.values());
      files.remove(NullVirtualFile.INSTANCE);
      return files;
    }

    final VirtualFile[] a = asArray();
    if (a != null) return Arrays.asList(a);

    return Collections.emptyList();
  }

  @NotNull
  private Map<String, VirtualFile> createMap() {
    return getFileSystem().isCaseSensitive()
           ? new THashMap<String, VirtualFile>()
           : new THashMap<String, VirtualFile>(CaseInsensitiveStringHashingStrategy.INSTANCE);
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
