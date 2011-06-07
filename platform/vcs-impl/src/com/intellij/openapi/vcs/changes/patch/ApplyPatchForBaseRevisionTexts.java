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
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ApplyPatchForBaseRevisionTexts {
  private final CharSequence myLocal;
  private CharSequence myBase;
  private final String myPatched;
  private ApplyPatchStatus myStatus;
  private List<String> myWarnings;
  private boolean myBaseRevisionLoaded;

  @NotNull
  public static ApplyPatchForBaseRevisionTexts create(final Project project, final VirtualFile file, final FilePath pathBeforeRename,
                                                       final TextFilePatch patch) throws VcsException {
    if (patch.isNewFile()) return createForAddition(patch);
    final String beforeVersionId = patch.getBeforeVersionId();
    if (beforeVersionId == null) {
      throw new VcsException(VcsBundle.message("patch.load.base.revision.error", pathBeforeRename, "No revision specified for base version."));
    }
    final DefaultPatchBaseVersionProvider provider = new DefaultPatchBaseVersionProvider(project, file, beforeVersionId);
    if (provider.canProvideContent()) {
      return new ApplyPatchForBaseRevisionTexts(provider, pathBeforeRename, patch, file);
    } else {
      if (! provider.hasVcs()) {
        throw new VcsException(VcsBundle.message("patch.load.base.revision.error", pathBeforeRename,
                                                 "Target file is not under version control."));
      } else {
        throw new VcsException(VcsBundle.message("patch.load.base.revision.error", pathBeforeRename, "Can not parse base revision."));
      }
    }
  }

  @NotNull
  private static ApplyPatchForBaseRevisionTexts createForAddition(final TextFilePatch patch) {
    return new ApplyPatchForBaseRevisionTexts("", "", patch.getNewFileText(), ApplyPatchStatus.SUCCESS);
  }

  private ApplyPatchForBaseRevisionTexts(CharSequence base,
                                        CharSequence local,
                                        String patched,
                                        ApplyPatchStatus status) {
    myBase = base;
    myLocal = local;
    myPatched = patched;
    myStatus = status;
    myWarnings = new ArrayList<String>();
  }

  ApplyPatchForBaseRevisionTexts(final DefaultPatchBaseVersionProvider provider, final FilePath pathBeforeRename, final TextFilePatch patch,
                                 final VirtualFile file) throws VcsException {
    myWarnings = new ArrayList<String>();
    myLocal = LoadTextUtil.loadText(file);
    final StringBuilder newText = new StringBuilder();
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
        myBaseRevisionLoaded = true;
        return false;
      }
    }, myWarnings);
    if ((! myBaseRevisionLoaded) || myStatus == null || ApplyPatchStatus.FAILURE.equals(myStatus)) {
      throw new VcsException(getCannotLoadBaseMessage(pathBeforeRename.getPath()));
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

  public static String getCannotLoadBaseMessage(final String filePatch) {
    return VcsBundle.message("patch.load.base.revision.error", filePatch,"");
  }
}
