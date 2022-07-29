// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorLocation;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Usage extends Navigatable {

  Usage[] EMPTY_ARRAY = new Usage[0];

  @NotNull
  UsagePresentation getPresentation();

  boolean isValid();

  boolean isReadOnly();

  @Nullable
  FileEditorLocation getLocation();

  void selectInEditor();

  void highlightInEditor();

  /**
   * @return offset of this usage in its containing file to which the corresponding "Go to Source" action should navigate,
   * or {@code -1} if the offset can't be computed for some reason.
   * This offset is used in "Find Usages" tool window tree to group usages by containing file and then by their offsets.
   * Please consider overriding this method if you implement {@link Usage} from scratch and can compute its offset efficiently.
   * The already existing implementations in the core, like {@link UsageInfo2UsageAdapter} implement this method efficiently enough, so there's no need to override them.
   * Also, please make your {@link Usage} implementation extend {@link com.intellij.usages.rules.UsageInFile} to be able to group usages in "Find Usages" tool window.
   */
  default int getNavigationOffset() {
    FileEditorLocation location = getLocation();
    if (location instanceof TextEditorLocation) {
      LogicalPosition position = ((TextEditorLocation)location).getPosition();
      return ((TextEditor)location.getEditor()).getEditor().logicalPositionToOffset(position);
    }
    return -1;
  }
}
