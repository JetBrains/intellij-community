// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.NamedColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 */
public class GradleConsoleFilter implements Filter {
  public static final Pattern LINE_AND_COLUMN_PATTERN = Pattern.compile("line (\\d+), column (\\d+)\\.");

  private final @Nullable Project myProject;
  private static final TextAttributes HYPERLINK_ATTRIBUTES =
    EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
  private String myFilteredFileName;
  private int myFilteredLineNumber;

  public GradleConsoleFilter(@Nullable Project project) {
    myProject = project;
  }

  @Override
  public @Nullable Result applyFilter(final @NotNull String line, final int entireLength) {
    String[] filePrefixes = new String[]{"Build file '", "build file '", "Settings file '", "settings file '"};
    String[] linePrefixes = new String[]{"' line: ", "': ", "' line: ", "': "};
    String filePrefix = null;
    String linePrefix = null;
    for (int i = 0; i < filePrefixes.length; i++) {
      int filePrefixIndex = StringUtil.indexOf(line, filePrefixes[i]);
      if (filePrefixIndex != -1) {
        filePrefix = filePrefixes[i];
        linePrefix = linePrefixes[i];
        break;
      }
    }

    if (filePrefix == null) {
      return null;
    }

    int filePrefixIndex = StringUtil.indexOf(line, filePrefix);

    final String fileAndLineNumber = line.substring(filePrefix.length() + filePrefixIndex);
    int linePrefixIndex = StringUtil.indexOf(fileAndLineNumber, linePrefix);

    if (linePrefixIndex == -1) {
      return null;
    }

    final String fileName = fileAndLineNumber.substring(0, linePrefixIndex);
    myFilteredFileName = fileName;
    String lineNumberStr = fileAndLineNumber.substring(linePrefixIndex + linePrefix.length()).trim();
    int lineNumberEndIndex = 0;
    for (int i = 0; i < lineNumberStr.length(); i++) {
      if (Character.isDigit(lineNumberStr.charAt(i))) {
        lineNumberEndIndex = i;
      }
      else {
        break;
      }
    }

    if (lineNumberStr.isEmpty()) {
      return null;
    }

    lineNumberStr = lineNumberStr.substring(0, lineNumberEndIndex + 1);
    int lineNumber;
    try {
      lineNumber = Integer.parseInt(lineNumberStr);
      myFilteredLineNumber = lineNumber;
    }
    catch (NumberFormatException e) {
      return null;
    }

    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(fileName.replace(File.separatorChar, '/'));
    if (file == null) {
      return null;
    }

    int textStartOffset = entireLength - line.length() + filePrefix.length() + filePrefixIndex;
    int highlightEndOffset = textStartOffset + fileName.length();
    OpenFileHyperlinkInfo info = null;
    if (myProject != null) {
      int columnNumber = 0;
      String lineAndColumn = StringUtil.substringAfterLast(line, " @ ");
      if (lineAndColumn != null) {
        Matcher matcher = LINE_AND_COLUMN_PATTERN.matcher(lineAndColumn);
        if (matcher.find()) {
          columnNumber = Integer.parseInt(matcher.group(2));
        }
      }
      info = new OpenFileHyperlinkInfo(myProject, file, Math.max(lineNumber - 1, 0), columnNumber);
    }
    TextAttributes attributes = HYPERLINK_ATTRIBUTES.clone();
    if (myProject != null && !ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(file)) {
      Color color = NamedColorUtil.getInactiveTextColor();
      attributes.setForegroundColor(color);
      attributes.setEffectColor(color);
    }
    return new Result(textStartOffset, highlightEndOffset, info, attributes);
  }

  public String getFilteredFileName() {
    return myFilteredFileName;
  }

  public int getFilteredLineNumber() {
    return myFilteredLineNumber;
  }
}
