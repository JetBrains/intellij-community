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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.ApplyPatchException;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

public class ApplyPatchForBaseRevisionTexts {
  private CharSequence myLocal;
  private CharSequence myBase;
  private String myPatched;
  private ApplyPatchStatus myStatus;
  private VcsException myException;

  @Nullable
  public static ApplyPatchForBaseRevisionTexts create(final Project project, final VirtualFile file, final FilePath pathBeforeRename,
                                                       final TextFilePatch patch) {

    final String beforeVersionId = patch.getBeforeVersionId();
    if (beforeVersionId == null) {
      return null;
    }
    final DefaultPatchBaseVersionProvider provider = new DefaultPatchBaseVersionProvider(project, file, beforeVersionId);
    if (provider.canProvideContent()) {
      return new ApplyPatchForBaseRevisionTexts(provider, pathBeforeRename, patch, file);
    }
    return null;
  }

  ApplyPatchForBaseRevisionTexts(final DefaultPatchBaseVersionProvider provider, final FilePath pathBeforeRename, final TextFilePatch patch,
                                 final VirtualFile file) {
      myLocal = LoadTextUtil.loadText(file);
      final StringBuilder newText = new StringBuilder();
      try {
        provider.getBaseVersionContent(pathBeforeRename, new Processor<CharSequence>() {
          public boolean process(final CharSequence text) {
            newText.setLength(0);
            try {
              myStatus = ApplyFilePatchBase.applyModifications(patch, text, newText);
            }
            catch(ApplyPatchException ex) {
              return true;  // continue to older versions
            }
            myBase = text;
            return false;
          }
        });
      }
      catch (VcsException vcsEx) {
        myException = vcsEx;
        myStatus = ApplyPatchStatus.FAILURE;
      }
      myPatched = newText.toString();
  }

  public CharSequence getLocal() {
    return myLocal;
  }

  public CharSequence getBase() {
    return myBase;
  }

  public String getPatched() {
    return myPatched;
  }

  public ApplyPatchStatus getStatus() {
    return myStatus;
  }

  public VcsException getException() {
    return myException;
  }
}
