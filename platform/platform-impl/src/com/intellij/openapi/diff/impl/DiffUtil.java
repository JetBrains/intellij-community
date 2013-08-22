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
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.util.FocusDiffSide;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class DiffUtil {
  private DiffUtil() {
  }

  public static void initDiffFrame(Project project, FrameWrapper frameWrapper, final DiffViewer diffPanel, final JComponent mainComponent) {
    frameWrapper.setComponent(mainComponent);
    frameWrapper.setProject(project);
    frameWrapper.setImage(ImageLoader.loadFromResource("/diff/Diff.png"));
    frameWrapper.setPreferredFocusedComponent(diffPanel.getPreferredFocusedComponent());
    frameWrapper.closeOnEsc();
  }

  @Nullable
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

  public static EditorEx createEditor(Document document, Project project, boolean isViewer) {
    EditorFactory factory = EditorFactory.getInstance();
    EditorEx editor = (EditorEx)(isViewer ? factory.createViewer(document, project) : factory.createEditor(document, project));
    editor.putUserData(DiffManagerImpl.EDITOR_IS_DIFF_KEY, Boolean.TRUE);
    editor.setSoftWrapAppliancePlace(SoftWrapAppliancePlaces.VCS_DIFF);
    editor.getGutterComponentEx().revalidateMarkup();
    return editor;
  }

  public static void drawBoldDottedFramingLines(Graphics2D g, int startX, int endX, int startY, int bottomY, Color color) {
    UIUtil.drawBoldDottedLine(g, startX, endX, startY, null, color, false);
    UIUtil.drawBoldDottedLine(g, startX, endX, bottomY, null, color, false);
  }

  public static void drawDoubleShadowedLine(@NotNull Graphics2D g, int startX, int endX, int y, @NotNull Color color) {
    UIUtil.drawLine(g, startX, y, endX, y, null, getFramingColor(color));
    UIUtil.drawLine(g, startX, y + 1, endX, y + 1, null, color);
  }

  public static Color getFramingColor(@NotNull Color backgroundColor) {
    return backgroundColor.darker();
  }

  @NotNull
  public static TextDiffType makeTextDiffType(@NotNull LineFragment fragment) {
    TextDiffType type = TextDiffType.create(fragment.getType());
    if (isInlineWrapper(fragment)) {
      return TextDiffType.deriveInstanceForInlineWrapperFragment(type);
    }
    return type;
  }

  public static boolean isInlineWrapper(@NotNull Fragment fragment) {
    return fragment instanceof LineFragment && ((LineFragment)fragment).getChildrenIterator() != null;
  }

  private static boolean isUnknownFileType(@NotNull DiffContent diffContent) {
    return FileTypes.UNKNOWN.equals(diffContent.getContentType());
  }

  public static boolean oneIsUnknown(@Nullable DiffContent content1, @Nullable DiffContent content2) {
    return content1 != null && isUnknownFileType(content1) || content2 != null && isUnknownFileType(content2);
  }

}
