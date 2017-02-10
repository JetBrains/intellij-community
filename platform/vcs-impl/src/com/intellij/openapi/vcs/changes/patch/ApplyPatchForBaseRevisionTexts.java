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

import com.intellij.openapi.diff.impl.patch.PatchHunk;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.text.StringUtil;
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
  private String myPatched;
  private List<String> myWarnings;
  private boolean myBaseRevisionLoaded;

  @NotNull
  public static ApplyPatchForBaseRevisionTexts create(final Project project, final VirtualFile file, final FilePath pathBeforeRename,
                                                       final TextFilePatch patch, final Getter<CharSequence> baseContents) {
    assert ! patch.isNewFile();
    final String beforeVersionId = patch.getBeforeVersionId();
    DefaultPatchBaseVersionProvider provider = null;
    if (beforeVersionId != null) {
      provider = new DefaultPatchBaseVersionProvider(project, file, beforeVersionId);
    }
    if (provider != null && provider.canProvideContent()) {
      return new ApplyPatchForBaseRevisionTexts(provider, pathBeforeRename, patch, file, baseContents);
    } else {
      return new ApplyPatchForBaseRevisionTexts(null, pathBeforeRename, patch, file, baseContents);
    }
  }

  private ApplyPatchForBaseRevisionTexts(final DefaultPatchBaseVersionProvider provider,
                                         final FilePath pathBeforeRename,
                                         final TextFilePatch patch,
                                         final VirtualFile file,
                                         Getter<CharSequence> baseContents) {
    myWarnings = new ArrayList<>();
    final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Document document = fileDocumentManager.getDocument(file);
    if (document != null) {
      fileDocumentManager.saveDocument(document);
    }
    myLocal = LoadTextUtil.loadText(file);

    final List<PatchHunk> hunks = patch.getHunks();

    if (provider != null) {
      try {
        provider.getBaseVersionContent(pathBeforeRename, new Processor<CharSequence>() {
          public boolean process(final CharSequence text) {
            final GenericPatchApplier applier = new GenericPatchApplier(text, hunks);
            if (! applier.execute()) {
              return true;
            }
            myBase = text;
            setPatched(applier.getAfter());
            return false;
          }
        }, myWarnings);
      }
      catch (VcsException e) {
        myWarnings.add(e.getMessage());
      }
      myBaseRevisionLoaded = myPatched != null;
      if (myBaseRevisionLoaded) return;
    }

    CharSequence contents = baseContents.get();
    if (contents != null) {
      contents = StringUtil.convertLineSeparators(contents.toString());
      myBase = contents;
      myBaseRevisionLoaded = true;
      final GenericPatchApplier applier = new GenericPatchApplier(contents, hunks);
      if (! applier.execute()) {
        applier.trySolveSomehow();
      }
      setPatched(applier.getAfter());
      return;
    }

    final GenericPatchApplier applier = new GenericPatchApplier(myLocal, hunks);
    if (! applier.execute()) {
      applier.trySolveSomehow();
    }
    setPatched(applier.getAfter());
  }

  public CharSequence getLocal() {
    return myLocal;
  }

  public CharSequence getBase() {
    return myBase;
  }
  
  private void setPatched(final String text) {
    myPatched = StringUtil.convertLineSeparators(text);
  }

  public String getPatched() {
    return myPatched;
  }

  public static String getCannotLoadBaseMessage(final String filePatch) {
    return VcsBundle.message("patch.load.base.revision.error", filePatch,"");
  }
}
