// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.patch.GitPatchWriter;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.*;

public final class UnifiedDiffWriter {
  @NonNls private static final String INDEX_SIGNATURE = "Index: {0}{1}";
  @NonNls public static final String ADDITIONAL_PREFIX = "IDEA additional info:";
  @NonNls public static final String ADD_INFO_HEADER = "Subsystem: ";
  @NonNls public static final String ADD_INFO_LINE_START = "<+>";
  private static final String HEADER_SEPARATOR = "===================================================================";
  @NonNls public static final String NO_NEWLINE_SIGNATURE = "\\ No newline at end of file";

  private UnifiedDiffWriter() {
  }

  public static void write(@Nullable Project project, Collection<? extends FilePatch> patches, Writer writer, final String lineSeparator,
                           @Nullable final CommitContext commitContext) throws IOException {
    write(project, patches, writer, lineSeparator, getPatchExtensions(project), commitContext);
  }

  @NotNull
  public static List<PatchEP> getPatchExtensions(@Nullable Project project) {
    return project == null ? Collections.emptyList() : PatchEP.EP_NAME.getExtensions(project);
  }

  public static void write(@Nullable Project project, Collection<? extends FilePatch> patches, Writer writer, final String lineSeparator,
                           final List<? extends PatchEP> extensions, final CommitContext commitContext) throws IOException {
    write(project, project == null ? null : project.getBasePath(), patches, writer, lineSeparator, extensions, commitContext);
  }

  public static void write(@Nullable Project project,
                           @Nullable String basePath,
                           Collection<? extends FilePatch> patches,
                           Writer writer,
                           final String lineSeparator,
                           @NotNull final List<? extends PatchEP> extensions,
                           final CommitContext commitContext) throws IOException {
    //write the patch files without content modifications strictly after the files with content modifications,
    // because GitPatchReader is not ready for mixed style patches
    List<FilePatch> noContentPatches = new ArrayList<>();
    for (FilePatch filePatch : patches) {
      if (!(filePatch instanceof TextFilePatch)) continue;
      TextFilePatch patch = (TextFilePatch)filePatch;
      if (patch.hasNoModifiedContent()) {
        noContentPatches.add(patch);
        continue;
      }
      @Nullable String t = patch.getBeforeName() == null ? patch.getAfterName() : patch.getBeforeName();
      String path = Objects.requireNonNull(t);
      String pathRelatedToProjectDir =
        project == null ? path : getPathRelatedToDir(Objects.requireNonNull(project.getBasePath()), basePath, path);
      final Map<String, CharSequence> additionalMap = new HashMap<>();
      for (PatchEP extension : extensions) {
        final CharSequence charSequence = extension.provideContent(pathRelatedToProjectDir, commitContext);
        if (! StringUtil.isEmpty(charSequence)) {
          additionalMap.put(extension.getName(), charSequence);
        }
      }
      String fileContentLineSeparator = ObjectUtils.coalesce(patch.getLineSeparator(), lineSeparator, "\n");
      writeFileHeading(patch, writer, lineSeparator, additionalMap);
      for(PatchHunk hunk: patch.getHunks()) {
        writeHunkStart(writer, hunk.getStartLineBefore(), hunk.getEndLineBefore(), hunk.getStartLineAfter(), hunk.getEndLineAfter(),
                       lineSeparator);
        for(PatchLine line: hunk.getLines()) {
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
            writer.write(fileContentLineSeparator);
          }
        }
      }
    }
    for (FilePatch patch : noContentPatches) {
      GitPatchWriter.writeGitHeader(writer, basePath, patch);
    }
  }

  @NotNull
  private static String getPathRelatedToDir(@NotNull String newBaseDir, @Nullable String basePath, @NotNull String path) {
    if (basePath == null) return path;
    String result = FileUtil.getRelativePath(new File(newBaseDir), new File(basePath, path));
    return result == null ? path : result;
  }

  private static void writeFileHeading(final FilePatch patch,
                                       final Writer writer,
                                       final String lineSeparator,
                                       Map<String, CharSequence> additionalMap) throws IOException {
    writer.write(MessageFormat.format(INDEX_SIGNATURE, patch.getBeforeName(), lineSeparator));
    if (additionalMap != null && ! additionalMap.isEmpty()) {
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
    writer.write(HEADER_SEPARATOR + lineSeparator);
    writeRevisionHeading(writer, "---", patch.getBeforeName(), patch.getBeforeVersionId(), lineSeparator);
    writeRevisionHeading(writer, "+++", patch.getAfterName(), patch.getAfterVersionId(), lineSeparator);
  }

  private static void writeRevisionHeading(final Writer writer, final String prefix,
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
    StringBuilder builder = new StringBuilder("@@ -");
    builder.append(startLine1+1).append(",").append(endLine1-startLine1);
    builder.append(" +").append(startLine2+1).append(",").append(endLine2-startLine2).append(" @@").append(lineSeparator);
    writer.append(builder.toString());
  }

  private static void writeLine(final Writer writer, final String line, final char prefix) throws IOException {
    writer.write(prefix);
    writer.write(line);
  }
}
