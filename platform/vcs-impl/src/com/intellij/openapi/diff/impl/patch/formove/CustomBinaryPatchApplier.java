package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public interface CustomBinaryPatchApplier {
  @NotNull
  ApplyPatchStatus apply(List<Pair<VirtualFile, FilePatch>> patches) throws IOException;
  @NotNull
  List<FilePatch> getAppliedPatches();
}
