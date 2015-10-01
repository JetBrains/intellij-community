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
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class ApplyFilePatchBase<T extends FilePatch> implements ApplyFilePatch {
  protected final static Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase");
  protected final T myPatch;

  public ApplyFilePatchBase(T patch) {
    myPatch = patch;
  }

  public T getPatch() {
    return myPatch;
  }

  public Result apply(final VirtualFile fileToPatch,
                      final ApplyPatchContext context,
                      final Project project,
                      FilePath pathBeforeRename,
                      Getter<CharSequence> baseContents,
                      CommitContext commitContext) throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("apply patch called for : " + fileToPatch.getPath());
    }
    if (myPatch.isNewFile()) {
      applyCreate(project, fileToPatch, commitContext);
    } else if (myPatch.isDeletedFile()) {
      FileEditorManager.getInstance(project).closeFile(fileToPatch);
      fileToPatch.delete(this);
    }
    else {
      return applyChange(project, fileToPatch, pathBeforeRename, baseContents);
    }
    return SUCCESS;
  }

  protected abstract void applyCreate(Project project, VirtualFile newFile, CommitContext commitContext) throws IOException;

  protected abstract Result applyChange(Project project, VirtualFile fileToPatch, FilePath pathBeforeRename, Getter<CharSequence> baseContents) throws IOException;

  @Nullable
  public static VirtualFile findPatchTarget(final ApplyPatchContext context, final String beforeName, final String afterName,
                                            final boolean isNewFile) throws IOException {
    VirtualFile file = null;
    if (beforeName != null) {
      file = findFileToPatchByName(context, beforeName, isNewFile);
    }
    if (file == null) {
      file = findFileToPatchByName(context, afterName, isNewFile);
    }
    else if (context.isAllowRename() && afterName != null && !beforeName.equals(afterName)) {
      String[] beforeNameComponents = beforeName.split("/");
      String[] afterNameComponents = afterName.split("/");
      if (!beforeNameComponents [beforeNameComponents.length-1].equals(afterNameComponents [afterNameComponents.length-1])) {
        context.registerBeforeRename(file);
        file.rename(FilePatch.class, afterNameComponents [afterNameComponents.length-1]);
      }
      boolean needMove = (beforeNameComponents.length != afterNameComponents.length);
      if (!needMove) {
        needMove = checkPackageRename(context, beforeNameComponents, afterNameComponents);
      }
      if (needMove) {
        VirtualFile moveTarget = findFileToPatchByComponents(context, afterNameComponents, afterNameComponents.length-1);
        if (moveTarget == null) {
          return null;
        }
        context.registerBeforeRename(file);
        file.move(FilePatch.class, moveTarget);
      }
    }
    return file;
  }

  private static boolean checkPackageRename(final ApplyPatchContext context,
                                            final String[] beforeNameComponents,
                                            final String[] afterNameComponents) {
    int changedIndex = -1;
    for(int i=context.getSkipTopDirs(); i<afterNameComponents.length-1; i++) {
      if (!beforeNameComponents [i].equals(afterNameComponents [i])) {
        if (changedIndex != -1) {
          return true;
        }
        changedIndex = i;
      }
    }
    if (changedIndex == -1) return false;
    VirtualFile oldDir = findFileToPatchByComponents(context, beforeNameComponents, changedIndex+1);
    VirtualFile newDir = findFileToPatchByComponents(context.getPrepareContext(), afterNameComponents, changedIndex+1);
    if (oldDir != null && newDir == null) {
      return false;
    }
    return true;
  }

  @Nullable
  private static VirtualFile findFileToPatchByName(@NotNull ApplyPatchContext context, final String fileName,
                                                   boolean isNewFile) {
    String[] pathNameComponents = fileName.split("/");
    int lastComponentToFind = isNewFile ? pathNameComponents.length-1 : pathNameComponents.length;
    return findFileToPatchByComponents(context, pathNameComponents, lastComponentToFind);
  }

  @Nullable
  private static VirtualFile findFileToPatchByComponents(ApplyPatchContext context,
                                                         final String[] pathNameComponents,
                                                         final int lastComponentToFind) {
    VirtualFile patchedDir = context.getBaseDir();
    for(int i=context.getSkipTopDirs(); i<lastComponentToFind; i++) {
      VirtualFile nextChild;
      if (pathNameComponents [i].equals("..")) {
        nextChild = patchedDir.getParent();
      }
      else {
        nextChild = patchedDir.findChild(pathNameComponents [i]);
      }
      if (nextChild == null) {
        if (context.isCreateDirectories()) {
          try {
            nextChild = patchedDir.createChildDirectory(null, pathNameComponents [i]);
          }
          catch (IOException e) {
            return null;
          }
        }
        else {
          return null;
        }
      }
      patchedDir = nextChild;
    }
    return patchedDir;
  }

  /*@Nullable
  public static ApplyPatchStatus applyModifications(final TextFilePatch patch, final CharSequence text, final StringBuilder newText) throws
                                                                                                                                     ApplyPatchException {
    final List<PatchHunk> hunks = patch.getHunks();
    if (hunks.isEmpty()) {
      return ApplyPatchStatus.SUCCESS;
    }
    List<String> lines = new ArrayList<String>();
    Collections.addAll(lines, LineTokenizer.tokenize(text, false));
    ApplyPatchStatus result = null;
    for(PatchHunk hunk: hunks) {
      result = ApplyPatchStatus.and(result, new ApplyPatchHunk(hunk).apply(lines));
    }
    for(int i=0; i<lines.size(); i++) {
      newText.append(lines.get(i));
      if (i < lines.size()-1 || !hunks.get(hunks.size()-1).isNoNewLineAtEnd()) {
        newText.append("\n");
      }
    }
    return result;
  }*/
}
