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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 24.11.2006
 * Time: 15:29:45
 */
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class UnifiedDiffWriter implements PatchWriter {
  @NonNls private static final String INDEX_SIGNATURE = "Index: {0}{1}";
  @NonNls public static final String ADDITIONAL_PREFIX = "IDEA additional info:";
  @NonNls public static final String ADD_INFO_HEADER = "Subsystem: ";
  @NonNls public static final String ADD_INFO_LINE_START = "<+>";
  private static final String HEADER_SEPARATOR = "===================================================================";
  @NonNls public static final String NO_NEWLINE_SIGNATURE = "\\ No newline at end of file";
  @Nullable private final Project myProject;

  protected UnifiedDiffWriter(@Nullable Project project) {
    myProject = project;
  }

  public static void write(Project project, Collection<FilePatch> patches, Writer writer, final String lineSeparator,
                           @Nullable final CommitContext commitContext) throws IOException {
    new UnifiedDiffWriter(project).write(patches, writer, lineSeparator, commitContext);
  }

  /**
   * @deprecated To remove in IDEA 2017.
   */
  @SuppressWarnings({"UnusedParameters", "unused"})
  @Deprecated
  public static void write(Project project, Collection<FilePatch> patches, Writer writer, final String lineSeparator,
                           final PatchEP[] extensions, final CommitContext commitContext) throws IOException {
    write(project, patches, writer, lineSeparator, commitContext);
  }


  public void write(Collection<FilePatch> patches, Writer writer, final String lineSeparator,
                    final CommitContext commitContext) throws IOException {

    for (FilePatch filePatch : patches) {
      if (!(filePatch instanceof TextFilePatch)) continue;
      TextFilePatch patch = (TextFilePatch)filePatch;

      writeAdditionalInfo(patch, writer, lineSeparator, commitContext);
      writePathHeading(writer, lineSeparator, patch);
      writeHunks(writer, lineSeparator, patch);
    }
  }


  @Override
  public void writePathHeading(@NotNull Writer writer, @NotNull String lineSeparator, @NotNull TextFilePatch patch) throws IOException {
    writeRevisionHeading(writer, "---", patch.getBeforeName(), patch.getBeforeVersionId(), lineSeparator);
    writeRevisionHeading(writer, "+++", patch.getAfterName(), patch.getAfterVersionId(), lineSeparator);
  }

  @Override
  public void writeHunks(@NotNull Writer writer, @NotNull String lineSeparator, @NotNull TextFilePatch patch) throws IOException {
    for (PatchHunk hunk : patch.getHunks()) {
      writeHunkStart(writer, hunk.getStartLineBefore(), hunk.getEndLineBefore(), hunk.getStartLineAfter(), hunk.getEndLineAfter(),
                     lineSeparator);
      for (PatchLine line : hunk.getLines()) {
        char prefixChar = ' ';
        switch (line.getType()) {
          case ADD:
            prefixChar = '+';
            break;
          case REMOVE:
            prefixChar = '-';
            break;
          case CONTEXT:
            prefixChar = ' ';
            break;
        }
        String text = line.getText();
        text = StringUtil.trimEnd(text, "\n");
        writeLine(writer, text, prefixChar);
        if (line.isSuppressNewLine()) {
          writer.write(lineSeparator + NO_NEWLINE_SIGNATURE + lineSeparator);
        }
        else {
          writer.write(lineSeparator);
        }
      }
    }
  }

  @Override
  public void writeAdditionalInfo(@NotNull FilePatch patch,
                                  @NotNull Writer writer,
                                  @NotNull String lineSeparator, @Nullable CommitContext commitContext) throws IOException {

    final String path = patch.getBeforeName() == null ? patch.getAfterName() : patch.getBeforeName();
    writer.write(MessageFormat.format(INDEX_SIGNATURE, patch.getBeforeName(), lineSeparator));
    final Map<String, CharSequence> additionalMap = constructAdditionalInfoMap(commitContext, path);
    writeAdditionalInfoMap(writer, lineSeparator, additionalMap);
    writer.write(HEADER_SEPARATOR + lineSeparator);
  }

  private static void writeAdditionalInfoMap(@NotNull Writer writer, @NotNull String lineSeparator, Map<String, CharSequence> additionalMap)
    throws IOException {
    if (!additionalMap.isEmpty()) {
      writer.write(ADDITIONAL_PREFIX);
      writer.write(lineSeparator);
      for (Map.Entry<String, CharSequence> entry : additionalMap.entrySet()) {
        writer.write(ADD_INFO_HEADER + entry.getKey());
        writer.write(lineSeparator);
        final String value = StringUtil.escapeStringCharacters(entry.getValue().toString());
        final List<String> lines = StringUtil.split(value, "\n");
        for (String line : lines) {
          writer.write(ADD_INFO_LINE_START);
          writer.write(line);
          writer.write(lineSeparator);
        }
      }
    }
  }

  @NotNull
  protected Map<String, CharSequence> constructAdditionalInfoMap(@Nullable CommitContext commitContext, String path) {
    final PatchEP[] extensions = myProject == null ? new PatchEP[0] : Extensions.getExtensions(PatchEP.EP_NAME, myProject);
    final Map<String, CharSequence> additionalMap = new HashMap<String, CharSequence>();
    for (PatchEP extension : extensions) {
      final CharSequence charSequence = extension.provideContent(path, commitContext);
      if (!StringUtil.isEmpty(charSequence)) {
        additionalMap.put(extension.getName(), charSequence);
      }
    }
    return additionalMap;
  }

  protected static void writeRevisionHeading(final Writer writer, final String prefix,
                                             final String revisionPath, final String revisionName,
                                             final String lineSeparator)
    throws IOException {
    writer.write(prefix + " ");
    writer.write(revisionPath);
    writer.write("\t");
    if (!StringUtil.isEmptyOrSpaces(revisionName)) {
      writer.write(revisionName);
    }
    writer.write(lineSeparator);
  }

  private static void writeHunkStart(Writer writer, int startLine1, int endLine1, int startLine2, int endLine2,
                                     final String lineSeparator)
    throws IOException {
    String builder = "@@ -" + (startLine1 + 1) + "," + (endLine1 - startLine1) +
                     " +" + (startLine2 + 1) + "," + (endLine2 - startLine2) + " @@" + lineSeparator;
    writer.append(builder);
  }

  private static void writeLine(final Writer writer, final String line, final char prefix) throws IOException {
    writer.write(prefix);
    writer.write(line);
  }
}
