/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.javafx;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * JavaFx File type
 *
 * @author andrey, Alexey.Ivanov
 */
public class JavaFxFileType extends LanguageFileType {
  /**
   * File icon in project explorer
   */
  static final Icon ICON = IconLoader.getIcon("javafxFile.png");

  /**
   * Singleton instance
   */
  public static final JavaFxFileType INSTANCE = new JavaFxFileType();

  private JavaFxFileType() {
    super(JavaFxLanguage.INSTANCE);
  }

  @NotNull
  public String getName() {
    return "JavaFx";
  }

  @NotNull
  public String getDescription() {
    return "JavaFx Script Files";
  }

  @NotNull
  public String getDefaultExtension() {
    return "fx";
  }

  public Icon getIcon() {
    return ICON;
  }

  //@Override
  //public EditorHighlighter getEditorHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile, @NotNull EditorColorsScheme colors) {
  //  return new JavaFxEditorHighlighter(colors, project, virtualFile);
  //}
}
