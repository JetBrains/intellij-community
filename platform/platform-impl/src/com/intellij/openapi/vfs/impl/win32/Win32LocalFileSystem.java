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
package com.intellij.openapi.vfs.impl.win32;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class Win32LocalFileSystem extends LocalFileSystemBase {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.win32.Win32LocalFileSystem");

  private static boolean ourIsAvailable;

  static {
    try {
      System.loadLibrary("IdeaWin32");
      ourIsAvailable = true;
    }
    catch (Throwable e) {
      ourIsAvailable = false;
    }
  }

  public static boolean isAvailable() {
    return ourIsAvailable;
  }

  private static final ThreadLocal<Win32LocalFileSystem> THREAD_LOCAL = new ThreadLocal<Win32LocalFileSystem>() {
    @Override
    protected Win32LocalFileSystem initialValue() {
      return new Win32LocalFileSystem();
    }
  };

  public static Win32LocalFileSystem getWin32Instance() {
    if (!isAvailable()) throw new RuntimeException("dll is not loaded");
    Win32LocalFileSystem fileSystem = THREAD_LOCAL.get();
    fileSystem.myKernel.clearCache();
    return fileSystem;
  }

  private final Win32Kernel myKernel = new Win32Kernel();
  public static boolean checkMe = false;

  private Win32LocalFileSystem() {
  }

  @Override
  public String[] list(VirtualFile file) {
    try {
      String[] strings = myKernel.list(file.getPath());
      if (checkMe && !Arrays.asList(strings).equals(Arrays.asList(super.list(file)))) {
        LOG.error(file.getPath());
      }
      return strings;
    }
    catch (Exception e) {
      if (checkMe) {
        assert false;
      }
      return super.list(file);
    }
  }

  @Override
  public boolean exists(VirtualFile fileOrDirectory) {
    if (fileOrDirectory.getParent() == null) return true;
    boolean b = myKernel.exists(fileOrDirectory.getPath());
    if (checkMe && b != super.exists(fileOrDirectory)) {
      LOG.error(fileOrDirectory.getPath());
    }
    return b;
  }

  @Override
  public boolean isDirectory(VirtualFile file) {
    try {
      boolean b = myKernel.isDirectory(file.getPath());
      if (checkMe && b != super.isDirectory(file)) {
        LOG.error(file.getPath());
      }
      return b;
    }
    catch (FileNotFoundException e) {
      return super.isDirectory(file);
    }
  }

  @Override
  public boolean isWritable(VirtualFile file) {
    try {
      boolean b = myKernel.isWritable(file.getPath());
      if (checkMe && b != super.isWritable(file)) {
        LOG.error(file.getPath());
      }
      return b;
    }
    catch (FileNotFoundException e) {
      return super.isWritable(file);
    }
  }

  @Override
  public long getTimeStamp(VirtualFile file) {
    try {
      long timeStamp = myKernel.getTimeStamp(file.getPath());
      if (checkMe && timeStamp != super.getTimeStamp(file)) {
        timeStamp = myKernel.getTimeStamp(file.getPath());
        LOG.error(file.getPath());
      }
      return timeStamp;
    }
    catch (FileNotFoundException e) {
      return super.getTimeStamp(file);
    }
  }

  @Override
  public WatchRequest addRootToWatch(@NotNull String rootPath, boolean toWatchRecursively) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Set<WatchRequest> addRootsToWatch(@NotNull Collection<String> rootPaths, boolean toWatchRecursively) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeWatchedRoots(@NotNull Collection<WatchRequest> rootsToWatch) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeWatchedRoot(@NotNull WatchRequest watchRequest) {
    throw new UnsupportedOperationException();
  }
}
