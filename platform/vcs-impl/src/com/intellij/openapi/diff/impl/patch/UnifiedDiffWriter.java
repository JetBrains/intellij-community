// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.patch.GitPatchWriter;
import com.intellij.project.ProjectKt;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;

import static com.intellij.openapi.vcs.changes.patch.PatchWriter.shouldForceUnixLineSeparator;

public final class UnifiedDiffWriter {
  @NonNls private static final String INDEX_SIGNATURE = "Index: {0}{1}";
  @NonNls public static final String ADDITIONAL_PREFIX = "IDEA additional info:";
  @NonNls public static final String ADD_INFO_HEADER = "Subsystem: ";
  @NonNls public static final String ADD_INFO_LINE_START = "<+>";
  private static final String HEADER_SEPARATOR = "===================================================================";
  @NonNls public static final String NO_NEWLINE_SIGNATURE = "\\ No newline at end of file";
  @NonNls public static final String DEV_NULL = "/dev/null";
  @NonNls public static final String A_PREFIX = "a/";
  @NonNls public static final String B_PREFIX = "b/";

  private UnifiedDiffWriter() {
  }

  public static void write(@Nullable Project project,
                           @NotNull Collection<? extends FilePatch> patches,
                           @NotNull Writer writer,
                           @NotNull String lineSeparator,
                           @Nullable CommitContext commitContext) throws IOException {
    write(project, project == null ? null : ProjectKt.getStateStore(project).getProjectBasePath(), patches, writer, lineSeparator,
          commitContext, null);
  }

  /**
   * @param lineSeparator Line separator to use for header lines,
   *                      and for content lines if {@link TextFilePatch#getLineSeparator()} was not specified.
   */
  public static void write(@Nullable Project project,
                           @Nullable Path basePath,
                           @NotNull Collection<? extends FilePatch> patches,
                           @NotNull Writer writer,
                           @NotNull String lineSeparator,
                           @Nullable CommitContext commitContext,
                           @Nullable List<PatchEP> patchEpExtensions) throws IOException {
    boolean forceUnixSeparators = shouldForceUnixLineSeparator(project);
    String headerLineSeparator = forceUnixSeparators ? "\n" : lineSeparator;

    // write the patch files without content modifications strictly after the files with content modifications,
    // because GitPatchReader is not ready for mixed style patches
    List<FilePatch> noContentPatches = new ArrayList<>();
    for (FilePatch filePatch : patches) {
      if (!(filePatch instanceof TextFilePatch)) continue;
      TextFilePatch patch = (TextFilePatch)filePatch;
      if (patch.hasNoModifiedContent()) {
        noContentPatches.add(patch);
        continue;
      }
      String path = ObjectUtils.chooseNotNull(patch.getAfterName(), patch.getBeforeName());
      String pathRelatedToProjectDir = getPathRelatedToProjectDir(project, basePath, path);
      Map<String, CharSequence> additionalMap = new HashMap<>();
      if (project != null) {
        for (PatchEP extension : (patchEpExtensions == null ? PatchEP.EP_NAME.getExtensionList() : patchEpExtensions)) {
          CharSequence charSequence = extension.provideContent(project, pathRelatedToProjectDir, commitContext);
          if (!StringUtil.isEmpty(charSequence)) {
            additionalMap.put(extension.getName(), charSequence);
          }
        }
      }

      String fileContentLineSeparator = patch.getLineSeparator();
      if (fileContentLineSeparator == null || forceUnixSeparators) {
        fileContentLineSeparator = headerLineSeparator;
      }

      writeFileHeading(writer, basePath, patch, headerLineSeparator, additionalMap);
      for (PatchHunk hunk : patch.getHunks()) {
        writeHunkStart(writer, hunk.getStartLineBefore(), hunk.getEndLineBefore(), hunk.getStartLineAfter(), hunk.getEndLineAfter(),
                       headerLineSeparator);
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
            // do not use fileContentLineSeparator here, as this line has no own separator
            writer.write(headerLineSeparator + NO_NEWLINE_SIGNATURE + headerLineSeparator);
          }
          else {
            writer.write(fileContentLineSeparator);
          }
        }
      }
    }
    for (FilePatch patch : noContentPatches) {
      GitPatchWriter.writeGitHeader(writer, basePath, patch, headerLineSeparator);
    }
  }

  private static String getPathRelatedToProjectDir(@Nullable Project project, @Nullable Path patchBasePath, @NotNull String filePath) {
    if (project == null || patchBasePath == null) return filePath;
    String newBaseDir = project.getBasePath();
    if (newBaseDir == null) return filePath;
    String relativePath = FileUtil.getRelativePath(new File(newBaseDir), new File(patchBasePath.toString(), filePath));
    if (relativePath == null) return filePath;
    return relativePath;
  }

  private static void writeFileHeading(@NotNull final Writer writer,
                                       @Nullable Path basePath,
                                       @NotNull final FilePatch patch,
                                       @NotNull final String lineSeparator,
                                       @Nullable Map<String, CharSequence> additionalMap) throws IOException {
    writer.write(MessageFormat.format(INDEX_SIGNATURE, patch.getBeforeName(), lineSeparator));
    writeAdditionalInfo(writer, lineSeparator, additionalMap);
    writer.write(HEADER_SEPARATOR + lineSeparator);
    GitPatchWriter.writeGitHeader(writer, basePath, patch, lineSeparator);
    writeRevisionHeading(writer, "---", getRevisionHeadingPath(patch, true),
                         patch.getBeforeVersionId(), lineSeparator);
    writeRevisionHeading(writer, "+++", getRevisionHeadingPath(patch, false),
                         patch.getAfterVersionId(), lineSeparator);
  }

  private static void writeAdditionalInfo(@NotNull Writer writer,
                                          @NotNull String lineSeparator,
                                          @Nullable Map<String, CharSequence> additionalMap) throws IOException {
    if (additionalMap != null && !additionalMap.isEmpty()) {
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

  @NonNls
  private static String getRevisionHeadingPath(@NotNull FilePatch patch, boolean beforePath) {
    if (beforePath) {
      return patch.isNewFile() ? DEV_NULL : A_PREFIX + patch.getBeforeName();
    }
    else {
      return patch.isDeletedFile() ? DEV_NULL : B_PREFIX + patch.getAfterName();
    }
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

  private static void writeHunkStart(@NotNull Writer writer, int startLine1, int endLine1, int startLine2, int endLine2,
                                     @NotNull String lineSeparator) throws IOException {
    writer.append(String.format("@@ -%s,%s +%s,%s @@",
                                startLine1 + 1, endLine1 - startLine1,
                                startLine2 + 1, endLine2 - startLine2));
    writer.append(lineSeparator);
  }

  private static void writeLine(@NotNull Writer writer, @NotNull String line, char prefix) throws IOException {
    writer.write(prefix);
    writer.write(line);
  }
}
