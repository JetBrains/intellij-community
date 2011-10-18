/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.CommitContext;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author irengrig
 *         Date: 2/25/11
 *         Time: 6:41 PM
 */
public class PatchWriter {
  private PatchWriter() {
  }

  public static void writePatches(final Project project,
                                  String fileName,
                                  List<FilePatch> patches,
                                  CommitContext commitContext,
                                  Charset charset) throws IOException {
    Writer writer = new OutputStreamWriter(new FileOutputStream(fileName), charset);
    try {
      final String lineSeparator = CodeStyleFacade.getInstance(project).getLineSeparator();
      UnifiedDiffWriter.write(project, patches, writer, lineSeparator, commitContext);
    }
    finally {
      writer.close();
    }
  }
}
