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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/14/12
 * Time: 3:07 PM
 */
public class ChangeForDiffConvertor implements Convertor<Change, DiffRequestPresentable> {
  private final Project myProject;
  private final boolean myRecursive;

  public ChangeForDiffConvertor(Project project, final boolean recursive) {
    myProject = project;
    myRecursive = recursive;
  }

  @Override
  public DiffRequestPresentable convert(Change o) {
    return convert(o, false);
  }

  public DiffRequestPresentable convert(final Change ch, final boolean forceText) {
    if (ch.hasOtherLayers() && myRecursive) {
      return new MultipleDiffRequestPresentable(myProject, ch);
    }
    if (ChangesUtil.isTextConflictingChange(ch)) {
      final AbstractVcs vcs = ChangesUtil.getVcsForChange(ch, myProject);
      if (vcs == null || vcs.getMergeProvider() == null) return null;
      final FilePath path = ChangesUtil.getFilePath(ch);
      VirtualFile vf = path.getVirtualFile();
      if (vf == null) {
        vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.getPath());
      }
      if (vf == null) return null;

      return new ConflictedDiffRequestPresentable(myProject, vf, ch);
    } else {
      if (forceText) {
        if (ch.getBeforeRevision() != null && ch.getAfterRevision() != null) {
          try {
            if (StringUtil.isEmptyOrSpaces(ch.getBeforeRevision().getContent()) &&
                StringUtil.isEmptyOrSpaces(ch.getAfterRevision().getContent())) {
              return null;
            }
            if (StringUtil.equals(ch.getBeforeRevision().getContent(), ch.getAfterRevision().getContent())) {
              return null;
            }
          }
          catch (VcsException e) {
            //
          }
        }
      }
      final ChangeDiffRequestPresentable presentable = new ChangeDiffRequestPresentable(myProject, ch);
      if (forceText) {
        presentable.setIgnoreDirectoryFlag(true);
      }
      return presentable;
    }
  }
}
