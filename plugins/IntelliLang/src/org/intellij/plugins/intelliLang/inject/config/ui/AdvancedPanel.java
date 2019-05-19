/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config.ui;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;

import javax.swing.*;

public class AdvancedPanel extends AbstractInjectionPanel<BaseInjection> {

  private JPanel myRoot;

  private EditorTextField myValuePattern;
  private JCheckBox mySingleFileCheckBox;

  public AdvancedPanel(Project project, BaseInjection injection) {
    super(injection, project);
    $$$setupUI$$$(); // see IDEA-9987
  }

  @Override
  protected void apply(BaseInjection other) {
    other.setValuePattern(myValuePattern.getText());
    other.setSingleFile(mySingleFileCheckBox.isSelected());
  }

  @Override
  protected void resetImpl() {
    myValuePattern.setText(myOrigInjection.getValuePattern());
    mySingleFileCheckBox.setSelected(myOrigInjection.isSingleFile());
  }

  @Override
  public JPanel getComponent() {
    return myRoot;
  }

  private void createUIComponents() {
    myValuePattern = new LanguageTextField(RegExpLanguage.INSTANCE, myProject, myOrigInjection.getValuePattern(), new LanguageTextField.SimpleDocumentCreator() {
      @Override
      public void customizePsiFile(PsiFile psiFile) {
        psiFile.putCopyableUserData(ValueRegExpAnnotator.KEY, Boolean.TRUE);
      }
    });
  }

  private void $$$setupUI$$$() {
  }
}
