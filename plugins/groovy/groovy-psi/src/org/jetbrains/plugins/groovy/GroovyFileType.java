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

package org.jetbrains.plugins.groovy;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.EditorHighlighterProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.highlighter.GroovyEditorHighlighter;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents Groovy file properites, such as extension etc.
 *
 * @author ilyas
 */
public class GroovyFileType extends LanguageFileType {
  public static final List<FileType> GROOVY_FILE_TYPES = new ArrayList<>();
  public static final GroovyFileType GROOVY_FILE_TYPE = new GroovyFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "groovy";

  private GroovyFileType() {
    super(GroovyLanguage.INSTANCE);
    FileTypeEditorHighlighterProviders.INSTANCE.addExplicitExtension(this, new EditorHighlighterProvider() {
      @Override
      public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                    @NotNull FileType fileType, @Nullable VirtualFile virtualFile,
                                                    @NotNull EditorColorsScheme colors) {
        return new GroovyEditorHighlighter(colors);
      }
    });
    GROOVY_FILE_TYPES.add(this);
  }

  @NotNull
  public static FileType[] getGroovyEnabledFileTypes() {
    return GROOVY_FILE_TYPES.toArray(new FileType[GROOVY_FILE_TYPES.size()]);
  }

  @Override
  @NotNull
  @NonNls
  public String getName() {
    return "Groovy";
  }

  @Override
  @NonNls
  @NotNull
  public String getDescription() {
    return "Groovy Files";
  }

  @Override
  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  public boolean isJVMDebuggingSupported() {
    return true;
  }
}
