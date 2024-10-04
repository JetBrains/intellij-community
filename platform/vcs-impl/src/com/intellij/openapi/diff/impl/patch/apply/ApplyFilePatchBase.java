// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Supplier;

@ApiStatus.Internal
public abstract class ApplyFilePatchBase<T extends FilePatch> implements ApplyFilePatch {
  protected final static Logger LOG = Logger.getInstance(ApplyFilePatchBase.class);
  protected final T myPatch;

  public ApplyFilePatchBase(T patch) {
    myPatch = patch;
  }

  public T getPatch() {
    return myPatch;
  }

  @Override
  public Result apply(@NotNull VirtualFile fileToPatch,
                      ApplyPatchContext context,
                      @NotNull Project project,
                      FilePath pathBeforeRename,
                      Supplier<? extends CharSequence> baseContents,
                      @Nullable CommitContext commitContext) throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("apply patch called for : " + fileToPatch.getPath());
    }
    if (myPatch.isNewFile()) {
      // File was already created by PathsVerifier.CheckAdded.check(), now set its content
      applyCreate(project, fileToPatch, commitContext);
    }
    else if (myPatch.isDeletedFile()) {
      FileEditorManager.getInstance(project).closeFile(fileToPatch);
      fileToPatch.delete(this);
    }
    else {
      return applyChange(project, fileToPatch, pathBeforeRename, baseContents);
    }
    return SUCCESS;
  }

  protected abstract void applyCreate(@NotNull Project project,
                                      @NotNull VirtualFile newFile,
                                      @Nullable CommitContext commitContext) throws IOException;

  protected abstract Result applyChange(@NotNull Project project,
                                        @NotNull VirtualFile fileToPatch,
                                        @NotNull FilePath pathBeforeRename,
                                        @Nullable Supplier<? extends CharSequence> baseContents) throws IOException;

  @Nullable
  public static VirtualFile findPatchTarget(final ApplyPatchContext context, final String beforeName, final String afterName)
    throws IOException {
    VirtualFile file = null;
    if (beforeName != null) {
      file = findFileToPatchByName(context, beforeName);
    }
    if (file == null) {
      file = findFileToPatchByName(context, afterName);
    }
    else if (context.isAllowRename() && afterName != null && !beforeName.equals(afterName)) {
      String[] beforeNameComponents = beforeName.split("/");
      String[] afterNameComponents = afterName.split("/");
      if (!beforeNameComponents[beforeNameComponents.length - 1].equals(afterNameComponents[afterNameComponents.length - 1])) {
        context.registerBeforeRename(file);
        file.rename(FilePatch.class, afterNameComponents[afterNameComponents.length - 1]);
      }
      boolean needMove = (beforeNameComponents.length != afterNameComponents.length);
      if (!needMove) {
        needMove = checkPackageRename(context, beforeNameComponents, afterNameComponents);
      }
      if (needMove) {
        VirtualFile moveTarget = findFileToPatchByComponents(context, afterNameComponents, afterNameComponents.length - 1);
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
    for (int i = context.getSkipTopDirs(); i < afterNameComponents.length - 1; i++) {
      if (!beforeNameComponents[i].equals(afterNameComponents[i])) {
        if (changedIndex != -1) {
          return true;
        }
        changedIndex = i;
      }
    }
    if (changedIndex == -1) return false;
    VirtualFile oldDir = findFileToPatchByComponents(context, beforeNameComponents, changedIndex + 1);
    VirtualFile newDir = findFileToPatchByComponents(context.getPrepareContext(), afterNameComponents, changedIndex + 1);
    if (oldDir != null && newDir == null) {
      return false;
    }
    return true;
  }

  @Nullable
  private static VirtualFile findFileToPatchByName(@NotNull ApplyPatchContext context, final String fileName) {
    String[] pathNameComponents = fileName.split("/");
    int lastComponentToFind = pathNameComponents.length;
    return findFileToPatchByComponents(context, pathNameComponents, lastComponentToFind);
  }

  @Nullable
  private static VirtualFile findFileToPatchByComponents(ApplyPatchContext context,
                                                         final String[] pathNameComponents,
                                                         final int lastComponentToFind) {
    VirtualFile patchedDir = context.getBaseDir();
    for (int i = context.getSkipTopDirs(); i < lastComponentToFind; i++) {
      VirtualFile nextChild;
      if (pathNameComponents[i].equals("..")) {
        nextChild = patchedDir.getParent();
      }
      else {
        nextChild = patchedDir.findChild(pathNameComponents[i]);
      }
      if (nextChild == null) {
        if (context.isCreateDirectories()) {
          try {
            nextChild = patchedDir.createChildDirectory(null, pathNameComponents[i]);
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
