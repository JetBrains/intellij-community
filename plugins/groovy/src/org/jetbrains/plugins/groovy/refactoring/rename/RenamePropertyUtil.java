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
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class RenamePropertyUtil {
  private RenamePropertyUtil() {
  }


  public static String getGetterNameByOldName(String propertyName, String oldGetterName) {
    return GroovyPropertyUtils.getAccessorName(oldGetterName.startsWith("is") ? "is" : "get", propertyName);
  }

  /**
   * @param m rename is invoked on member
   * @return true, if rename property
   */
  public static Pair<List<? extends PsiElement>, String> askToRenameProperty(PsiMember m) {
    if (m instanceof GrAccessorMethod) {
      return member(((GrAccessorMethod)m).getProperty());
    }
    final String name;
    if (m instanceof GrMethod) {
      name = GroovyPropertyUtils.getPropertyNameByAccessorName(m.getName());
      if (name == null) return member(m);
    }
    else if (m instanceof GrField) {
      name = m.getName();
      if (!((GrField)m).isProperty()) return member(m);
    }
    else {
      return member(m);
    }

    final PsiClass containingClass = m.getContainingClass();
    if (containingClass == null) return member(m);
    final boolean isStatic = m.hasModifierProperty(PsiModifier.STATIC);

    List<PsiElement> property = new ArrayList<>();
    assert name != null;
    ContainerUtil.addAll(property, GroovyPropertyUtils.getAllGetters(containingClass, name, isStatic, false));
    ContainerUtil.addAll(property, GroovyPropertyUtils.getAllSetters(containingClass, name, isStatic, false));

    for (Iterator<PsiElement> iterator = property.iterator(); iterator.hasNext(); ) {
      if (iterator.next() instanceof GrAccessorMethod) iterator.remove();
    }

    final PsiField field = containingClass.findFieldByName(name, false);
    if (field != null) {
      property.add(field);
    }
    if (property.size() == 1) return member(m);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return property(property, name);
    }

    AskDialog dialog = new AskDialog(m);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.CANCEL_EXIT_CODE) return cancel();
    if (dialog.renameProperty()) return property(property, name);
    return member(m);
  }

  private static Pair<List<? extends PsiElement>, String> property(List<PsiElement> list, String name) {
    return Pair.<List<? extends PsiElement>, String>create(list, name);
  }

  private static Pair<List<? extends PsiElement>, String> cancel() {
    return Pair.<List<? extends PsiElement>, String>create(Collections.<PsiElement>emptyList(), null);
  }

  private static Pair<List<? extends PsiElement>, String> member(PsiMember m) {
    return Pair.<List<? extends PsiElement>, String>create(Collections.singletonList(m), null);
  }

  private static class AskDialog extends DialogWrapper {
    private JRadioButton myRbRenameMember;
    private JRadioButton myRbRenameProperty;
    private final PsiMember myMember;


    protected AskDialog(PsiMember member) {
      super(member.getProject());
      myMember = member;
      setTitle(RefactoringBundle.message("select.refactoring.title"));
      init();
    }

    @Override
    protected JComponent createNorthPanel() {
      return new JLabel(RefactoringBundle.message("what.would.you.like.to.do"));
    }

    @Nullable
    private String getPropertyName() {
      if (myMember instanceof GrMethod) {
        return GroovyPropertyUtils.getPropertyNameByAccessorName(myMember.getName());
      }
      else if (myMember instanceof GrField) {
        return myMember.getName();
      }
      return null;
    }

    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel();

      myRbRenameMember = new JRadioButton(GroovyRefactoringBundle.message("rename.member", getDescription()));
      myRbRenameProperty = new JRadioButton(GroovyRefactoringBundle.message("rename.property", getPropertyName()));

      ButtonGroup gr = new ButtonGroup();
      gr.add(myRbRenameProperty);
      gr.add(myRbRenameMember);
      myRbRenameProperty.setSelected(true);

      Box box = Box.createVerticalBox();
      box.add(Box.createVerticalStrut(5));
      box.add(myRbRenameProperty);
      box.add(myRbRenameMember);
      panel.add(box, BorderLayout.CENTER);

      new RadioUpDownListener(myRbRenameMember, myRbRenameProperty);
      return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myRbRenameProperty;
    }

    private String getDescription() {
      return String.format("%s '%s'", UsageViewUtil.getType(myMember), myMember.getName());
    }

    public boolean renameProperty() {
      return myRbRenameProperty.isSelected();
    }
  }
}
