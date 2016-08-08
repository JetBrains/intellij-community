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
package com.intellij.openapi.diff.impl.external;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UIBasedFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class CompositeDiffTool implements DiffTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.external.CompositeDiffTool");
  private final List<DiffTool> myTools;

  public CompositeDiffTool(@NotNull List<DiffTool> tools) {
    myTools = new ArrayList<>(tools);
  }

  public CompositeDiffTool(@NotNull DiffTool[] tools) {
    myTools = Arrays.asList(tools);
  }

  public void show(DiffRequest data) {
    checkDiffData(data);
    DiffTool tool = chooseTool(data);
    if (tool != null) tool.show(data);
    else LOG.error("No diff tool found which is able to handle request " + data);
  }

  public boolean canShow(DiffRequest data) {
    checkDiffData(data);
    return chooseTool(data) != null;
  }

  @Override
  public DiffViewer createComponent(String title, DiffRequest request, Window window, @NotNull Disposable parentDisposable) {
    // should not be called for it
    throw new IllegalStateException();
  }

  @Nullable
  private DiffTool chooseTool(DiffRequest data) {
    final DiffContent[] contents = data.getContents();

    if (contents.length == 2) {
      final FileType type1 = contents[0].getContentType();
      final FileType type2 = contents[1].getContentType();
      if (type1 == type2 && type1 instanceof UIBasedFileType) {
        return BinaryDiffTool.INSTANCE;
      }

      //todo[kb] register or not this instance in common diff tools ?
      if (type1 == type2 && type1 instanceof ArchiveFileType) {
        return ArchiveDiffTool.INSTANCE;
      }
    }

    for (DiffTool tool : myTools) {
      if (tool.canShow(data)) return tool;
    }
    return null;
  }

  private static void checkDiffData(@NotNull DiffRequest data) {
    DiffContent[] contents = data.getContents();
    for (DiffContent content : contents) {
      LOG.assertTrue(content != null, "Null content in diff request");
    }
  }
}
