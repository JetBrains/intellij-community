/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class VcsDescriptor {

  private final String myName;
  private final boolean myCrawlUpToCheckUnderVcs;
  private final boolean myAreChildrenValidMappings;
  private final String myDisplayName;
  private final List<String> myAdministrativePatterns;

  public VcsDescriptor(String administrativePattern,
                       String displayName,
                       String name,
                       boolean crawlUpToCheckUnderVcs,
                       boolean areChildrenValidMappings) {
    myAdministrativePatterns = parseAdministrativePatterns(administrativePattern);
    myDisplayName = displayName;
    myName = name;
    myCrawlUpToCheckUnderVcs = crawlUpToCheckUnderVcs;
    myAreChildrenValidMappings = areChildrenValidMappings;
  }

  @NotNull
  private static List<String> parseAdministrativePatterns(@Nullable String administrativePattern) {
    if (administrativePattern == null) return Collections.emptyList();
    return ContainerUtil.map(administrativePattern.split(","), it -> it.trim());
  }

  public boolean areChildrenValidMappings() {
    return myAreChildrenValidMappings;
  }

  public boolean probablyUnderVcs(final VirtualFile file) {
    return probablyUnderVcs(file, myCrawlUpToCheckUnderVcs);
  }

  public boolean probablyUnderVcs(final VirtualFile file, boolean crawlUp) {
    if (file == null || !file.isDirectory() || !file.isValid()) return false;
    if (myAdministrativePatterns.isEmpty()) return false;

    if (crawlUp) {
      return ReadAction.compute(() -> {
        VirtualFile current = file;
        while (current != null) {
          if (matchesVcsDirPattern(current)) return true;
          current = current.getParent();
        }
        return false;
      });
    }
    else {
      return ReadAction.compute(() -> matchesVcsDirPattern(file));
    }
  }

  private boolean matchesVcsDirPattern(@NotNull VirtualFile file) {
    for (String pattern : myAdministrativePatterns) {
      VirtualFile child = file.findChild(pattern);
      if (child != null) return true;
    }
    return false;
  }

  public boolean hasVcsDirPattern() {
    return !myAdministrativePatterns.isEmpty();
  }

  public boolean matchesVcsDirPattern(@NotNull String dirName) {
    return myAdministrativePatterns.contains(dirName);
  }

  /**
   * @deprecated Prefer {@link AbstractVcs#getDisplayName()}
   */
  @Deprecated(forRemoval = true)
  public String getDisplayName() {
    return myDisplayName == null ? myName : myDisplayName;
  }

  public String getName() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsDescriptor that = (VcsDescriptor)o;
    return Objects.equals(myName, that.myName);
  }

  @Override
  public int hashCode() {
    return myName != null ? myName.hashCode() : 0;
  }

  @Override
  public String toString() {
    return myName;
  }
}
