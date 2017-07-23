/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchEP;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.List;

import static com.intellij.util.ObjectUtils.chooseNotNull;

public class ShelfFileProcessorUtil {
  public static void savePatchFile(@Nullable Project project,
                                   @NotNull File patchFile,
                                   List<FilePatch> patches,
                                   @Nullable PatchEP[] extensions,
                                   @NotNull CommitContext context) throws IOException {
    try (Writer writer = new OutputStreamWriter(new FileOutputStream(patchFile), CharsetToolkit.UTF8_CHARSET)) {
      UnifiedDiffWriter
        .write(project, patches, writer, "\n", chooseNotNull(extensions, UnifiedDiffWriter.getPatchExtensions(project)),
               context);
    }
  }
}