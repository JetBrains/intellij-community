// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import com.intellij.util.PatternUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

public final class IgnoredFileBean implements IgnoredFileDescriptor {
  private final String myPath;
  private final String myFilenameIfFile;
  private final String myMask;
  private final Pattern myPattern;
  private final IgnoreSettingsType myType;
  private final Project myProject;
  private volatile FilePath myCachedResolved;

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
    return matchesFile(VcsUtil.getFilePath(file));
  }

  @Override
  public boolean matchesFile(@NotNull FilePath filePath) {
    if (myType == IgnoreSettingsType.MASK) {
      return myPattern.matcher(filePath.getName()).matches();
    }
    else {
      // quick check for 'file' == exact match pattern
      if (IgnoreSettingsType.FILE.equals(myType) && !StringUtil.equals(myFilenameIfFile, filePath.getName())) return false;

      FilePath selector = resolve();
      if (selector == null) return false;

      if (myType == IgnoreSettingsType.FILE) {
        return Comparing.equal(selector, filePath);
      }
      else {
        if ("./".equals(myPath)) {
          // special case for ignoring the project base dir (IDEADEV-16056)
          return !filePath.isDirectory() && Comparing.equal(filePath.getParentPath(), selector);
        }
        return FileUtil.startsWith(filePath.getPath(), selector.getPath());
      }
    }
  }

  @Nullable
  private FilePath resolve() {
    if (myCachedResolved == null) {
      myCachedResolved = doResolve();
    }

    return myCachedResolved;
  }

  private @Nullable FilePath doResolve() {
    if (myProject == null || myProject.isDisposed()) {
      return null;
    }
    VirtualFile baseDir = myProject.getBaseDir();

    String path = FileUtil.toSystemIndependentName(myPath);
    if (baseDir == null) {
      return VcsUtil.getFilePath(path);
    }

    VirtualFile resolvedRelative = baseDir.findFileByRelativePath(path);
    if (resolvedRelative != null) return VcsUtil.getFilePath(resolvedRelative);

    return VcsUtil.getFilePath(path);
  }

  public void resetCache() {
    myCachedResolved = null;
  }
}
