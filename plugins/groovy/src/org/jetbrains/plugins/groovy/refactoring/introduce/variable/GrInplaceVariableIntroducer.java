/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrFinalListener;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrInplaceVariableIntroducer extends GrInplaceIntroducer {
  private JCheckBox myCanBeFinalCb;

  public GrInplaceVariableIntroducer(GrVariable elementToRename,
                                     Editor editor,
                                     Project project,
                                     String title,
                                     List<RangeMarker> occurrences,
                                     @Nullable PsiElement elementToIntroduce) {
    super(elementToRename, editor, project, title, occurrences, elementToIntroduce);
  }

  @Override
  public LinkedHashSet<String> suggestNames(GrIntroduceContext context) {
    return new GrVariableNameSuggester(context, new GroovyVariableValidator(context));
  }

  @Nullable
  @Override
  protected JComponent getComponent() {
    myCanBeFinalCb = new NonFocusableCheckBox("Declare final");
    myCanBeFinalCb.setSelected(false);
    myCanBeFinalCb.setMnemonic('f');
    final GrFinalListener finalListener = new GrFinalListener(myEditor);
    myCanBeFinalCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        new WriteCommandAction(myProject, getCommandName(), getCommandName()) {
          @Override
          protected void run(Result result) throws Throwable {
            PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
            final GrVariable variable = getVariable();
            if (variable != null) {
              finalListener.perform(myCanBeFinalCb.isSelected(), variable);
            }
          }
        }.execute();
      }
    });
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(null);

    if (myCanBeFinalCb != null) {
      panel.add(myCanBeFinalCb, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));
    }

    panel.add(Box.createVerticalBox(), new GridBagConstraints(0, 2, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0,0));

    return panel;

  }
}
