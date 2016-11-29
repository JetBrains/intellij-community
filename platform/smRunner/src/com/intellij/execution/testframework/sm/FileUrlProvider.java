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
package com.intellij.execution.testframework.sm;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class FileUrlProvider implements SMTestLocator, DumbAware {
  private static final Logger LOG = Logger.getInstance(FileUrlProvider.class.getName());

  public static final FileUrlProvider INSTANCE = new FileUrlProvider();

  @NotNull
  @Override
  public List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    if (!URLUtil.FILE_PROTOCOL.equals(protocol)) {
      return Collections.emptyList();
    }

    final String normalizedPath = path.replace(File.separatorChar, '/');

    final int lineNoSeparatorIndex = normalizedPath.lastIndexOf(':');

    final String filePath;
    final int lineNumber;
    // if line is specified
    if (lineNoSeparatorIndex > 3) {   // on Windows, paths start with /C: and that colon is not a line number separator 
      final String lineNumStr = normalizedPath.substring(lineNoSeparatorIndex + 1);
      int lineNum = 0;
      try {
        lineNum = Integer.parseInt(lineNumStr);
      } catch (NumberFormatException e) {
        LOG.warn(protocol + ": Malformed location path: " + path, e);
      }

      filePath = normalizedPath.substring(0, lineNoSeparatorIndex);
      lineNumber = lineNum;
    } else {
      // unknown line
      lineNumber = 1;
      filePath = normalizedPath;
    }
    // Now we should search file with most suitable path
    // here path may be absolute or relative
    final String systemIndependentPath = FileUtil.toSystemIndependentName(filePath);
    final List<VirtualFile> virtualFiles = TestsLocationProviderUtil.findSuitableFilesFor(systemIndependentPath, project);
    if (virtualFiles.isEmpty()) {
      return Collections.emptyList();
    }

    if (lineNumber < 0) {
      LOG.warn("Tests location provider: line number should be >= 1. Path: " + path);
    }

    final List<Location> locations = new ArrayList<>(2);
    for (VirtualFile file : virtualFiles) {
      locations.add(createLocationFor(project, file, lineNumber < 1 ? 1 : lineNumber));
    }
    return locations;
  }

  @Nullable
  public static Location createLocationFor(Project project, @NotNull VirtualFile virtualFile, int lineNum) {
    assert lineNum > 0;

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (psiFile == null) {
      return null;
    }

    final Document doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (doc == null) {
      return null;
    }

    final int lineCount = doc.getLineCount();
    final int lineStartOffset;
    final int endOffset;
    if (lineNum <= lineCount) {
      lineStartOffset = doc.getLineStartOffset(lineNum - 1);
      endOffset = doc.getLineEndOffset(lineNum - 1);
    } else {
      // unknown line
      lineStartOffset = 0;
      endOffset = doc.getTextLength();
    }

    int offset = lineStartOffset;
    PsiElement elementAtLine = null;
    while (offset <= endOffset) {
      elementAtLine = psiFile.findElementAt(offset);
      if (!(elementAtLine instanceof PsiWhiteSpace)) break;
      int length = elementAtLine.getTextLength();
      offset += length > 1 ? length - 1 : 1;
    }

    return PsiLocation.fromPsiElement(project, elementAtLine != null ? elementAtLine : psiFile);
  }
}
