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
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Max Medvedev
 */
public class GrCodeStyleImportsPanelWrapper extends CodeStyleAbstractPanel {

  private final GrCodeStyleImportsPanel myImportsPanel;

  protected GrCodeStyleImportsPanelWrapper(CodeStyleSettings settings) {
    super(settings);
    myImportsPanel = new GrCodeStyleImportsPanel();
  }


  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return null;
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  protected String getPreviewText() {
    return null;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    myImportsPanel.apply(settings.getCustomSettings(GroovyCodeStyleSettings.class));
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return myImportsPanel.isModified(settings.getCustomSettings(GroovyCodeStyleSettings.class));
  }

  @Override
  public JComponent getPanel() {
    return myImportsPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    myImportsPanel.reset(settings.getCustomSettings(GroovyCodeStyleSettings.class));
  }

  @Override
  protected String getTabTitle() {
    return ApplicationBundle.message("title.imports");
  }
}
