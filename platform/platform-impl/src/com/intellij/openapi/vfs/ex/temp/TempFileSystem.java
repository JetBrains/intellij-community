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
package com.intellij.openapi.vfs.ex.temp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TempFileSystem extends NewVirtualFileSystem {
  private final FSItem myRoot = new FSDir(null, "/");

  public static TempFileSystem getInstance() {
    return ApplicationManager.getApplication().getComponent(TempFileSystem.class);
  }

  public boolean isCaseSensitive() {
    return true;
  }

  protected String extractRootPath(@NotNull final String path) {
    return path.startsWith("/") ? "/" : "";
  }

  public int getRank() {
    return 1;
  }

  @Nullable
  private FSItem convert(VirtualFile file) {
    final VirtualFile parentFile = file.getParent();
    if (parentFile == null) return myRoot;
    FSItem parentItem = convert(parentFile);
    if (parentItem == null || !parentItem.isDirectory()) {
      return null;
    }

    return parentItem.findChild(file.getName());
  }

  public VirtualFile copyFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent, @NotNull final String copyName)
      throws IOException {
    return VfsUtil.copyFile(requestor, file, newParent, copyName);
  }

  @NotNull
  public VirtualFile createChildDirectory(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String dir) throws IOException {
    final FSItem fsItem = convert(parent);
    assert fsItem != null && fsItem.isDirectory();

    final FSDir fsDir = (FSDir)fsItem;
    final FSItem existingDir = fsDir.findChild(dir);
    if (existingDir == null) {
      fsDir.addChild(new FSDir(fsDir, dir));
    }
    else if (!existingDir.isDirectory()) {
      throw new IOException("Directory already contains a file named " + dir);
    }


    return new FakeVirtualFile(parent, dir);
  }

  public VirtualFile createChildFile(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String file) throws IOException {
    final FSItem fsItem = convert(parent);
    assert fsItem != null: "cannot find parent directory: " + parent.getPath();
    assert fsItem.isDirectory(): "parent is not a directory: " + parent.getPath();

    final FSDir fsDir = (FSDir)fsItem;

    assert fsDir.findChild(file) == null: "File " + file + " already exists in " + parent.getPath();
    fsDir.addChild(new FSFile(fsDir, file));

    return new FakeVirtualFile(parent, file);
  }

  public void deleteFile(final Object requestor, @NotNull final VirtualFile file) throws IOException {
    final FSItem fsItem = convert(file);
    assert fsItem != null: "failed to delete file " + file.getPath();
    fsItem.getParent().removeChild(fsItem);
  }

  public void moveFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent) throws IOException {
    final FSItem fsItem = convert(file);
    assert fsItem != null: "failed to move file " + file.getPath();
    final FSItem newParentItem = convert(newParent);
    assert newParentItem != null && newParentItem.isDirectory(): "failed to find move target " + file.getPath();
    FSDir newDir = (FSDir) newParentItem;
    if (newDir.findChild(file.getName()) != null) {
      throw new IOException("Directory already contains a file named " + file.getName());
    }

    fsItem.getParent().removeChild(fsItem);
    ((FSDir) newParentItem).addChild(fsItem);
  }

  public void renameFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final String newName) throws IOException {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    fsItem.setName(newName);
  }

  @NotNull
  public String getProtocol() {
    return "temp";
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    return convert(fileOrDirectory) != null;
  }

  public String[] list(final VirtualFile file) {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    return fsItem.list();
  }

  public boolean isDirectory(final VirtualFile file) {
    return convert(file) instanceof FSDir;
  }

  public long getTimeStamp(final VirtualFile file) {
    final FSItem fsItem = convert(file);
    assert fsItem != null: "cannot find item for path " + file.getPath();

    return fsItem.myTimestamp;
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) throws IOException {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    fsItem.myTimestamp = modstamp > 0 ? modstamp : LocalTimeCounter.currentTime();
  }

  public boolean isWritable(final VirtualFile file) {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    return fsItem.myWritable;
  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {
    final FSItem fsItem = convert(file);
    assert fsItem != null;

    fsItem.myWritable = writableFlag;
  }

  @NotNull
  public byte[] contentsToByteArray(final VirtualFile file) throws IOException {
    final FSItem fsItem = convert(file);
    if (fsItem == null) throw new FileNotFoundException("Cannot find temp for " + file.getPath());
    
    assert fsItem instanceof FSFile;

    return ((FSFile)fsItem).myContent;
  }

  @NotNull
  public InputStream getInputStream(final VirtualFile file) throws IOException {
    return new ByteArrayInputStream(contentsToByteArray(file));
  }

  @NotNull
  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp)
      throws IOException {
    return new ByteArrayOutputStream() {
      public void close() throws IOException {
        super.close();
        final FSItem fsItem = convert(file);
        assert fsItem instanceof FSFile;

        ((FSFile)fsItem).myContent = toByteArray();
        setTimeStamp(file, modStamp);
      }
    };
  }

  public long getLength(final VirtualFile file) {
    try {
      return contentsToByteArray(file).length;
    }
    catch (IOException e) {
      return 0;
    }
  }

  private static abstract class FSItem {
    private final FSDir myParent;
    private String myName;
    private long myTimestamp;
    private boolean myWritable;

    protected FSItem(final FSDir parent, final String name) {
      myParent = parent;
      myName = name;
      myTimestamp = LocalTimeCounter.currentTime();
      myWritable = true;
    }

    public abstract boolean isDirectory();

    @Nullable
    public FSItem findChild(final String name) {
      return null;
    }

    public void setName(final String name) {
      myName = name;
    }

    public FSDir getParent() {
      return myParent;
    }

    public String[] list() {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  private static class FSDir extends FSItem {
    private final List<FSItem> myChildren = new ArrayList<FSItem>();

    public FSDir(final FSDir parent, final String name) {
      super(parent, name);
    }

    @Nullable
    public FSItem findChild(final String name) {
      for (FSItem child : myChildren) {
        if (name.equals(child.myName)) {
          return child;
        }
      }

      return null;
    }

    public boolean isDirectory() {
      return true;
    }

    public void addChild(final FSItem item) {
      myChildren.add(item);
    }

    public void removeChild(final FSItem fsItem) {
      if (fsItem.myName.equals("src") && getParent() == null) {
        throw new RuntimeException("removing src directory");
      }
      myChildren.remove(fsItem);
    }

    public String[] list() {
      String[] names = ArrayUtil.newStringArray(myChildren.size());
      for (int i = 0; i < names.length; i++) {
        names[i] = myChildren.get(i).myName;
      }
      return names;
    }
  }

  private static class FSFile extends FSItem {
    public FSFile(final FSDir parent, final String name) {
      super(parent, name);
    }

    private byte[] myContent = new byte[0];

    public boolean isDirectory() {
      return false;
    }
  }
}
