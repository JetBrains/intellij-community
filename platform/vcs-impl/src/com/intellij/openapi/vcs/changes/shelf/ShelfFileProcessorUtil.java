// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchEP;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ShelfFileProcessorUtil {
  public static void savePatchFile(@Nullable Project project,
                                   @NotNull Path patchFile,
                                   List<? extends FilePatch> patches,
                                   @Nullable List<? extends PatchEP> extensions,
                                   @NotNull CommitContext context) throws IOException {
    try (Writer writer = Files.newBufferedWriter(patchFile)) {
      UnifiedDiffWriter.write(project, patches, writer, "\n", extensions == null ? UnifiedDiffWriter.getPatchExtensions(project) : extensions, context);
    }
  }
}