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
import com.intellij.util.Consumer;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.config.AbstractTagInjection;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.XPathSupportProxy;
import org.intellij.plugins.intelliLang.util.LanguageTextField;

import javax.swing.*;

public class AdvancedXmlPanel extends AbstractInjectionPanel<BaseInjection> {

  private JPanel myRoot;

  private EditorTextField myValuePattern;
  private EditorTextField myXPathCondition;
  private JLabel myXPathConditionLabel;
  private JCheckBox mySingleFileCheckBox;

  public AdvancedXmlPanel(Project project, BaseInjection injection) {
    super(injection, project);
    $$$setupUI$$$(); // see IDEA-9987
    if (!(injection instanceof AbstractTagInjection)) {
      myXPathCondition.setVisible(false);
      myXPathConditionLabel.setVisible(false);
    }

  }

  protected void apply(BaseInjection other) {
    other.setValuePattern(myValuePattern.getText());
    other.setSingleFile(mySingleFileCheckBox.isSelected());
    if (myOrigInjection instanceof AbstractTagInjection) {
      ((AbstractTagInjection)other).setXPathCondition(myXPathCondition.getText());
    }
  }

  protected void resetImpl() {
    myValuePattern.setText(myOrigInjection.getValuePattern());
    mySingleFileCheckBox.setSelected(myOrigInjection.isSingleFile());
    if (myOrigInjection instanceof AbstractTagInjection) {
      myXPathCondition.setText(((AbstractTagInjection)myOrigInjection).getXPathCondition());
    }
  }

  public JPanel getComponent() {
    return myRoot;
  }

  private void createUIComponents() {
    myValuePattern = new LanguageTextField(RegExpLanguage.INSTANCE, myProject, myOrigInjection.getValuePattern(), new Consumer<PsiFile>() {
      public void consume(PsiFile psiFile) {
        psiFile.putCopyableUserData(ValueRegExpAnnotator.KEY, Boolean.TRUE);
      }
    });

    if (myOrigInjection instanceof AbstractTagInjection) {
// don't even bother to look up the language when xpath-evaluation isn't possible
      final XPathSupportProxy proxy = XPathSupportProxy.getInstance();
      myXPathCondition = new LanguageTextField(proxy != null ? InjectedLanguage.findLanguageById("XPath") : null, myProject,
                                               ((AbstractTagInjection)myOrigInjection).getXPathCondition(), new Consumer<PsiFile>() {
        public void consume(PsiFile psiFile) {
          // important to get proper validation & completion for Jaxen's built-in and PSI functions
          // like lower-case(), file-type(), file-ext(), file-name(), etc.
          if (proxy != null) {
            proxy.attachContext(psiFile);
          }
        }
      });
    }
    else {
      myXPathCondition = new EditorTextField();
    }
  }

  private void $$$setupUI$$$() {
  }
}
