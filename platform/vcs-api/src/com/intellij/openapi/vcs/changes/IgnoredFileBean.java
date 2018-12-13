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

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.util.PathUtilRt;
import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

public class IgnoredFileBean implements IgnoredFileDescriptor {
  private final String myPath;
  private final String myFilenameIfFile;
  private final String myMask;
  private final Pattern myPattern;
  private final IgnoreSettingsType myType;
  private final Project myProject;
  private volatile VirtualFile myCachedResolved;

  IgnoredFileBean(@NotNull String path, @NotNull IgnoreSettingsType type, @Nullable Project project) {
    myPath = path;
    myType = type;
    myFilenameIfFile = IgnoreSettingsType.FILE.equals(type) ? PathUtilRt.getFileName(path) : null;
    myProject = project;
    myMask = null;
    myPattern = null;
  }

  IgnoredFileBean(@NotNull String mask) {
    myType = IgnoreSettingsType.MASK;
    myMask = mask;
    myPattern = PatternUtil.fromMask(mask);
    myPath = null;
    myFilenameIfFile = null;
    myProject = null;
  }

  @Nullable
  Project getProject() {
    return myProject;
  }

  @Override
  @Nullable
  public String getPath() {
    return myPath;
  }

  @Override
  @Nullable
  public String getMask() {
    return myMask;
  }

  @Override
  @NotNull
  public IgnoreSettingsType getType() {
    return myType;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IgnoredFileBean that = (IgnoredFileBean)o;

    if (!Objects.equals(myPath, that.myPath)) return false;
    if (!Objects.equals(myMask, that.myMask)) return false;
    if (myType != that.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPath != null ? myPath.hashCode() : 0;
    result = 31 * result + (myMask != null ? myMask.hashCode() : 0);
    result = 31 * result + myType.hashCode();
    return result;
  }

  @Override
  public boolean matchesFile(@NotNull VirtualFile file) {
    if (myType == IgnoreSettingsType.MASK) {
      return myPattern.matcher(file.getName()).matches();
    }
    else {
      // quick check for 'file' == exact match pattern
      if (IgnoreSettingsType.FILE.equals(myType) && !StringUtil.equals(myFilenameIfFile, file.getNameSequence())) return false;

      VirtualFile selector = resolve();
      if (Comparing.equal(selector, NullVirtualFile.INSTANCE)) return false;

      if (myType == IgnoreSettingsType.FILE) {
        return Comparing.equal(selector, file);
      }
      else {
        if ("./".equals(myPath)) {
          // special case for ignoring the project base dir (IDEADEV-16056)
          return !file.isDirectory() && Comparing.equal(file.getParent(), selector);
        }
        return VfsUtilCore.isAncestor(selector, file, false);
      }
    }
  }

  @NotNull
  private VirtualFile resolve() {
    if (myCachedResolved == null) {
      VirtualFile resolved = doResolve();
      myCachedResolved = resolved != null ? resolved : NullVirtualFile.INSTANCE;
    }

    return myCachedResolved;
  }

  @Nullable
  private VirtualFile doResolve() {
    if (myProject == null || myProject.isDisposed()) {
      return null;
    }
    VirtualFile baseDir = myProject.getBaseDir();

    String path = FileUtil.toSystemIndependentName(myPath);
    if (baseDir == null) {
      return LocalFileSystem.getInstance().findFileByPath(path);
    }

    VirtualFile resolvedRelative = baseDir.findFileByRelativePath(path);
    if (resolvedRelative != null) return resolvedRelative;

    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  public void resetCache() {
    myCachedResolved = null;
  }
}
