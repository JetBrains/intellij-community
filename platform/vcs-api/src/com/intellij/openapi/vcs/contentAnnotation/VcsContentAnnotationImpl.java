/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.diff.DiffMixin;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/3/11
 * Time: 1:04 PM
 */
public class VcsContentAnnotationImpl implements VcsContentAnnotation {
  private final Project myProject;
  private final VcsContentAnnotationSettings mySettings;

  public static VcsContentAnnotation getInstance(final Project project) {
    return ServiceManager.getService(project, VcsContentAnnotation.class);
  }

  public VcsContentAnnotationImpl(Project project, VcsContentAnnotationSettings settings) {
    myProject = project;
    mySettings = settings;
  }

  @Nullable
  @Override
  public Details annotateLine(final VirtualFile vf, final BeforeAfter<Integer> enclosingRange, final int lineNumber) {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final AbstractVcs vcs = vcsManager.getVcsFor(vf);
    if (vcs == null) return null;
    if (vcs.getDiffProvider() instanceof DiffMixin) {
      boolean fileRecent = false;
      final VcsRevisionDescription description = ((DiffMixin)vcs.getDiffProvider()).getCurrentRevisionDescription(vf);
      final Date date = description.getRevisionDate();
      if (date.getTime() > (System.currentTimeMillis() - mySettings.getLimit())) {
        fileRecent = true;
      }
      return new Details(false, false, fileRecent, null);
    }
    return null;
  }
}
