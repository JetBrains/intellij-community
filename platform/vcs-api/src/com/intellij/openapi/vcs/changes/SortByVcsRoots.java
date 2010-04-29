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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;

import java.util.Collection;

public class SortByVcsRoots<T> {
  private final Project myProject;
  private final Convertor<T, FilePath> myConvertor;
  private ProjectLevelVcsManager myVcsManager;
  public static final VcsRoot ourFictiveValue = new VcsRoot(null, null);

  public SortByVcsRoots(Project project, final Convertor<T, FilePath> convertor) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myConvertor = convertor;
  }

  public MultiMap<VcsRoot, T> sort(final Collection<T> in) {
    final MultiMap<VcsRoot, T> result = new MultiMap<VcsRoot,T>();
    for (T t : in) {
      final VcsRoot root = myVcsManager.getVcsRootObjectFor(myConvertor.convert(t));
      if (root != null) {
        result.putValue(root, t);
      } else {
        result.putValue(ourFictiveValue, t);
      }
    }
    return result;
  }
}
