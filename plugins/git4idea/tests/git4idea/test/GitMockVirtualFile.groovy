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
package git4idea.test

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

/**
 * VirtualFile test implementation based on {@link java.io.File}.
 *
 * @author Kirill Likhodedov
 */
class GitMockVirtualFile extends VirtualFile {

  private String myPath;

  GitMockVirtualFile(@NotNull String path) {
    myPath = path;
  }

  @Override
  String getName() {
    new File(path).getName();
  }

  @Override
  VirtualFileSystem getFileSystem() {
    throw new UnsupportedOperationException();
  }

  @Override
  String getPath() {
    myPath;
  }

  @Override
  boolean isWritable() {
    throw new UnsupportedOperationException();
  }

  @Override
  boolean isDirectory() {
    new File(path).directory;
  }

  @Override
  boolean isValid() {
    new File(myPath).exists()
  }

  @Override
  @Nullable
  VirtualFile getParent() {
    File parentFile = FileUtil.getParentFile(new File(path))
    parentFile ? new GitMockVirtualFile(parentFile.path) : null;
  }

  @Override
  VirtualFile[] getChildren() {
    new File(path).list().collect { new GitMockVirtualFile("$path/$it") }
  }

  @Override
  OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  byte[] contentsToByteArray() {
    throw new UnsupportedOperationException();
  }

  @Override
  long getTimeStamp() {
    throw new UnsupportedOperationException();
  }

  @Override
  long getLength() {
    throw new UnsupportedOperationException();
  }

  @Override
  void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    throw new UnsupportedOperationException();
  }

  @Override
  InputStream getInputStream() {
    throw new UnsupportedOperationException();
  }

  @Override
  String toString() {
    return myPath;
  }
}
