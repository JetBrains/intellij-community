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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;

public class VcsDescriptor implements Comparable<VcsDescriptor> {
  private final String myName;
  private final boolean myCrawlUpToCheckUnderVcs;
  private final String myDisplayName;
  private final String myAdministrativePattern;
  private boolean myIsNone;

  public VcsDescriptor(String administrativePattern, String displayName, String name, boolean crawlUpToCheckUnderVcs) {
    myAdministrativePattern = administrativePattern;
    myDisplayName = displayName;
    myName = name;
    myCrawlUpToCheckUnderVcs = crawlUpToCheckUnderVcs;
  }

  public boolean probablyUnderVcs(final VirtualFile file) {
    if (file == null || (! file.isDirectory()) || (! file.isValid())) return false;
    if (myAdministrativePattern == null) return false;
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (checkFileForBeingAdministrative(file)) return true;
        if (myCrawlUpToCheckUnderVcs) {
          VirtualFile current = file.getParent();
          while (current != null) {
            if (checkFileForBeingAdministrative(current)) return true;
            current = current.getParent();
          }
        }
        return false;
      }
    });
  }

  private boolean checkFileForBeingAdministrative(final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        final String[] patterns = myAdministrativePattern.split(",");
        for (String pattern : patterns) {
          final VirtualFile child = file.findChild(pattern.trim());
          if (child != null) return true;
        }
        return false;
      }
    });
  }

  public String getDisplayName() {
    return myDisplayName == null ? myName : myDisplayName;
  }

  public String getName() {
    return myName;
  }

  @Override
  public int compareTo(VcsDescriptor o) {
    return Comparing.compare(myDisplayName, o.myDisplayName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsDescriptor that = (VcsDescriptor)o;

    if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;

    return true;
  }

  public boolean isNone() {
    return myIsNone;
  }

  public static VcsDescriptor createFictive() {
    final VcsDescriptor vcsDescriptor = new VcsDescriptor(null, VcsBundle.message("none.vcs.presentation"), null, false);
    vcsDescriptor.myIsNone = true;
    return vcsDescriptor;
  }

  @Override
  public int hashCode() {
    return myName != null ? myName.hashCode() : 0;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }
}
