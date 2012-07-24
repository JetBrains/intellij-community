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
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.12.2006
 * Time: 15:24:28
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;

public class IgnoredFileBean {
  private final String myPath;
  private final String myMask;
  private final Matcher myMatcher;
  private final IgnoreSettingsType myType;
  private final Project myProject;
  private VirtualFile myCachedResolved;

  IgnoredFileBean(String path, IgnoreSettingsType type, Project project) {
    myPath = path;
    myType = type;
    myProject = project;
    myMask = null;
    myMatcher = null;
  }

  IgnoredFileBean(String mask) {
    myType = IgnoreSettingsType.MASK;
    myMask = mask;
    if (mask == null) {
      myMatcher = null;
    }
    else {
      myMatcher = PatternUtil.fromMask(mask).matcher("");
    }
    myPath = null;
    myProject = null;
  }

  @Nullable
  public String getPath() {
    return myPath;
  }

  @Nullable
  public String getMask() {
    return myMask;
  }

  public IgnoreSettingsType getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IgnoredFileBean that = (IgnoredFileBean)o;

    if (myPath != null ? !myPath.equals(that.myPath) : that.myPath != null) return false;
    if (myMask != null ? !myMask.equals(that.myMask) : that.myMask != null) return false;
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

  public boolean matchesFile(VirtualFile file) {
    if (myType == IgnoreSettingsType.MASK) {
      myMatcher.reset(file.getName());
      return myMatcher.matches();
    }
    else {
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
        return VfsUtil.isAncestor(selector, file, false);
      }
    }
  }

  private VirtualFile resolve() {
    if (myCachedResolved == null) {
      VirtualFile resolved = doResolve();
      myCachedResolved = resolved != null ? resolved : NullVirtualFile.INSTANCE;
    }

    return myCachedResolved;
  }

  @Nullable
  private VirtualFile doResolve() {
    if (myProject == null || myProject.isDisposed()) { return null; }
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
