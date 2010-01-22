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

package org.jetbrains.plugins.groovy;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.highlighter.GroovyEditorHighlighter;

import javax.swing.*;

/**
 * Represents Groovy file properites, such as extension etc.
 *
 * @author ilyas
 */
public class GroovyFileType extends LanguageFileType {

  public static final GroovyFileType GROOVY_FILE_TYPE = new GroovyFileType();
  public static final Language GROOVY_LANGUAGE = GROOVY_FILE_TYPE.getLanguage();
  @NonNls public static final String DEFAULT_EXTENSION = "groovy";

  private GroovyFileType() {
    super(new GroovyLanguage());
  }

  @NotNull
  @NonNls
  public String getName() {
    return "Groovy";
  }

  @NonNls
  @NotNull
  public String getDescription() {
    return "Groovy Files";
  }

  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  public Icon getIcon() {
    return GroovyIcons.GROOVY_ICON_16x16;
  }

  public boolean isJVMDebuggingSupported() {
    return true;
  }

  public EditorHighlighter getEditorHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile, @NotNull EditorColorsScheme colors) {
    return new GroovyEditorHighlighter(colors, project, virtualFile);
  }
}
