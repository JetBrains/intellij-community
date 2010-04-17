/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffContentUtil;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.diff.impl.util.FocusDiffSide;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.NotNull;

public class DiffUtil {
  private DiffUtil() {
  }

  public static void initDiffFrame(FrameWrapper frameWrapper, final DiffPanelImpl diffPanel) {
    Project project = diffPanel.getProject();
    frameWrapper.setComponent(diffPanel.getComponent());
    frameWrapper.setProject(project);
    frameWrapper.setImage(ImageLoader.loadFromResource("/diff/Diff.png"));
    frameWrapper.setPreferredFocusedComponent(diffPanel.getPreferredFocusedComponent());
    frameWrapper.closeOnEsc();
    frameWrapper.addDisposable(diffPanel);
  }

  public static FocusDiffSide getFocusDiffSide(DataContext dataContext) {
    return FocusDiffSide.DATA_KEY.getData(dataContext);
  }

  public static String[] convertToLines(@NotNull String text) {
    return new LineTokenizer(text).execute();
  }

  public static FileType[] chooseContentTypes(DiffContent[] contents) {
    FileType commonType = FileTypes.PLAIN_TEXT;
    for (DiffContent content : contents) {
      FileType contentType = content.getContentType();
      if (DiffContentUtil.isTextType(contentType)) commonType = contentType;
    }
    FileType[] result = new FileType[contents.length];
    for (int i = 0; i < contents.length; i++) {
      FileType contentType = contents[i].getContentType();
      result[i] = DiffContentUtil.isTextType(contentType) ? contentType : commonType;
    }
    return result;
  }

  public static boolean isWritable(DiffContent content) {
    Document document = content.getDocument();
    return document != null && document.isWritable();
  }

  public static int getTextLength(String text) {
    return text != null ? text.length() : 0;
  }

  public static boolean isEmpty(DiffFragment fragment) {
    return getTextLength(fragment.getText1()) == 0 &&
           getTextLength(fragment.getText2()) == 0;
  }

  public static EditorEx createEditor(Document document, Project project, boolean isViewer) {
    EditorFactory factory = EditorFactory.getInstance();
    EditorEx editor = (EditorEx)(isViewer ? factory.createViewer(document, project) : factory.createEditor(document, project));
    editor.putUserData(DiffManagerImpl.EDITOR_IS_DIFF_KEY, Boolean.TRUE);
    editor.getGutterComponentEx().revalidateMarkup();
    return editor;
  }
}
