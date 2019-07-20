// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchEP;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.intellij.util.ObjectUtils.chooseNotNull;

public class ShelfFileProcessorUtil {
  public static void savePatchFile(@Nullable Project project,
                                   @NotNull File patchFile,
                                   List<? extends FilePatch> patches,
                                   @Nullable List<? extends PatchEP> extensions,
                                   @NotNull CommitContext context) throws IOException {
    try (Writer writer = new OutputStreamWriter(new FileOutputStream(patchFile), StandardCharsets.UTF_8)) {
      UnifiedDiffWriter.write(project, patches, writer, "\n", chooseNotNull(extensions, UnifiedDiffWriter.getPatchExtensions(project)), context);
    }
  }
}