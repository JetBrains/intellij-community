/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff.actions;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;

/**
 * @author Jeka
 */
@Deprecated
public class ClipboardVsValueContents extends DiffRequest {

  private DiffContent[] myContents = null;
  private final String myValue;

  public ClipboardVsValueContents(String value, Project project) {
    super(project);
    myValue = value;
  }

  public String[] getContentTitles() {
    return new String[]{
      DiffBundle.message("diff.content.clipboard.content.title"),
      DiffBundle.message("diff.content.selected.value")
    };
  }

  @Override
  public boolean isSafeToCallFromUpdate() {
    return !SystemInfo.isMac;
  }

  @NotNull
  public DiffContent[] getContents() {
    if (myContents != null) return myContents;
    DiffContent clipboardContent = createClipboardContent();
    if (clipboardContent == null) clipboardContent = new SimpleContent("");

    myContents = new DiffContent[2];
    myContents[0] = clipboardContent;

    myContents[1] = new SimpleContent(myValue);
    return myContents;
  }

  public String getWindowTitle() {
    return DiffBundle.message("diff.clipboard.vs.value.dialog.title");
  }

  @Nullable
  public static DiffContent createClipboardContent() {
    String text = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    return text != null ? new SimpleContent(text) : null;
  }
}
