// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.PatchHunk;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.chooseNotNull;

public class ApplyPatchForBaseRevisionTexts {
  private static final Logger LOG = Logger.getInstance(ApplyPatchForBaseRevisionTexts.class);

  private final @NotNull String myLocal;
  private final @Nullable String myBase;
  private final @NotNull String myPatched;
  private final boolean myIsAppliedSomehow;

  public ApplyPatchForBaseRevisionTexts(@NotNull String patched, @NotNull String local, @Nullable String base, boolean isAppliedSomehow) {
    myLocal = local;
    myBase = base;
    myPatched = patched;
    myIsAppliedSomehow = isAppliedSomehow;
  }

  public @NotNull String getLocal() {
    return myLocal;
  }

  public @Nullable String getBase() {
    return myBase;
  }

  public @NotNull String getPatched() {
    return myPatched;
  }

  public boolean isAppliedSomehow() {
    return myIsAppliedSomehow;
  }

  public boolean isBaseRevisionLoaded() {
    return myBase != null;
  }

  @CalledInAny
  public static @NotNull ApplyPatchForBaseRevisionTexts create(@NotNull Project project,
                                                               @NotNull VirtualFile file,
                                                               @NotNull FilePath pathBeforeRename,
                                                               @NotNull TextFilePatch patch,
                                                               @Nullable CharSequence baseContents) {
    assert !patch.isNewFile();

    String localContent = getLocalFileContent(file);

    if (baseContents != null) {
      ApplyPatchForBaseRevisionTexts result = createFromStoredBase(localContent, patch, baseContents);
      if (result != null) return result;
    }

    String beforeVersionId = patch.getBeforeVersionId();
    if (beforeVersionId != null) {
      ApplyPatchForBaseRevisionTexts result =
        createFromBaseVersionProvider(project, localContent, patch, beforeVersionId, file, pathBeforeRename);
      if (result != null) return result;
    }

    return createFromLocal(localContent, patch);
  }

  private static @NotNull ApplyPatchForBaseRevisionTexts createFromLocal(@NotNull String localContent, @NotNull TextFilePatch patch) {
    GenericPatchApplier.AppliedSomehowPatch appliedPatch = GenericPatchApplier.applySomehow(localContent, patch.getHunks());

    String patchedContent = StringUtil.convertLineSeparators(appliedPatch.patchedText);
    return new ApplyPatchForBaseRevisionTexts(patchedContent, localContent, null, appliedPatch.isAppliedSomehow);
  }

  private static @Nullable ApplyPatchForBaseRevisionTexts createFromBaseVersionProvider(@NotNull Project project,
                                                                                        @NotNull String localContent,
                                                                                        @NotNull TextFilePatch patch,
                                                                                        @NotNull String beforeVersionId,
                                                                                        @NotNull VirtualFile file,
                                                                                        @NotNull FilePath pathBeforeRename) {
    try {
      List<PatchHunk> hunks = patch.getHunks();

      Ref<String> baseRef = new Ref<>();
      Ref<String> patchedRef = new Ref<>();

      DefaultPatchBaseVersionProvider.getBaseVersionContent(project, beforeVersionId, file, pathBeforeRename, base -> {
        GenericPatchApplier.AppliedPatch appliedPatch = GenericPatchApplier.apply(base, hunks);
        if (appliedPatch == null) return true;

        baseRef.set(base);
        patchedRef.set(StringUtil.convertLineSeparators(appliedPatch.patchedText));
        return false;
      });

      String base = baseRef.get();
      String patched = patchedRef.get();
      if (base == null || patched == null) return null;

      return new ApplyPatchForBaseRevisionTexts(patched, localContent, base, false);
    }
    catch (VcsException e) {
      LOG.warn(e);
      return null;
    }
  }

  private static @Nullable ApplyPatchForBaseRevisionTexts createFromStoredBase(@NotNull String localContent,
                                                                               @NotNull TextFilePatch patch,
                                                                               @NotNull CharSequence baseContents) {
    final List<PatchHunk> hunks = patch.getHunks();

    String base = baseContents.toString();
    GenericPatchApplier.AppliedPatch appliedPatch = GenericPatchApplier.apply(base, hunks);

    if (appliedPatch == null) {
      LOG.warn(VcsBundle.message("patch.apply.wrong.base.and.can.t.be.applied.warning",
                                 chooseNotNull(patch.getBeforeName(), patch.getAfterName())));

      return null;
    }

    String patched = StringUtil.convertLineSeparators(appliedPatch.patchedText);

    return new ApplyPatchForBaseRevisionTexts(patched, localContent, base, false);
  }

  private static @NotNull String getLocalFileContent(@NotNull VirtualFile file) {
    return ReadAction.compute(() -> {
      Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        return document.getText();
      }
      return LoadTextUtil.loadText(file).toString();
    });
  }
}
