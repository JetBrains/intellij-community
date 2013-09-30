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
package org.jetbrains.plugins.groovy.refactoring.memberPullUp;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.memberPullUp.PullUpDialogBase;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.ui.AbstractMemberSelectionTable;
import com.intellij.refactoring.ui.ClassCellRenderer;
import com.intellij.refactoring.ui.DocCommentPanel;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.refactoring.classMembers.GrMemberInfo;
import org.jetbrains.plugins.groovy.refactoring.classMembers.GrMemberInfoStorage;
import org.jetbrains.plugins.groovy.refactoring.classMembers.GrMemberSelectionTable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Max Medvedev
 */
class GrPullUpDialog extends PullUpDialogBase<GrMemberInfoStorage, GrMemberInfo, GrMember, PsiClass> {
  private final Callback myCallback;
  private DocCommentPanel myJavaDocPanel;

  private final InterfaceContainmentVerifier myInterfaceContainmentVerifier = new InterfaceContainmentVerifier() {
    public boolean checkedInterfacesContain(PsiMethod psiMethod) {
      return PullUpHelper.checkedInterfacesContain(myMemberInfos, psiMethod);
    }
  };

  private static final String PULL_UP_STATISTICS_KEY = "pull.up##";

  public interface Callback {
    boolean checkConflicts(GrPullUpDialog dialog);
  }

  public GrPullUpDialog(Project project,
                        PsiClass typeDefinition,
                        List<PsiClass> superClasses,
                        GrMemberInfoStorage storage,
                        GrPullUpHandler handler) {
    super(project, typeDefinition, superClasses, storage, GrPullUpHandler.REFACTORING_NAME);

    myCallback = handler;

    init();
  }

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.memberPullUp.PullUpDialog";
  }

  InterfaceContainmentVerifier getContainmentVerifier() {
    return myInterfaceContainmentVerifier;
  }

  @Override
  protected void updateMemberInfo() {
    super.updateMemberInfo();
    if (myMemberSelectionPanel != null) {
      ((MyMemberInfoModel)myMemberInfoModel).setSuperClass(getSuperClass());
      myMemberSelectionPanel.getTable().setMemberInfos(myMemberInfos);
      myMemberSelectionPanel.getTable().fireExternalDataChange();
    }
  }

  @Override
  protected void initClassCombo(JComboBox classCombo) {
    classCombo.setRenderer(new ClassCellRenderer(classCombo.getRenderer()));
  }

  protected PsiClass getPreselection() {
    PsiClass preselection = RefactoringHierarchyUtil.getNearestBaseClass(myClass, false);

    final String statKey = PULL_UP_STATISTICS_KEY + myClass.getQualifiedName();
    for (StatisticsInfo info : StatisticsManager.getInstance().getAllValues(statKey)) {
      final String superClassName = info.getValue();
      PsiClass superClass = null;
      for (PsiClass aClass : mySuperClasses) {
        if (Comparing.strEqual(superClassName, aClass.getQualifiedName())) {
          superClass = aClass;
          break;
        }
      }
      if (superClass != null && StatisticsManager.getInstance().getUseCount(info) > 0) {
        preselection = superClass;
        break;
      }
    }
    return preselection;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MEMBERS_PULL_UP);
  }

  protected void doAction() {
    if (!myCallback.checkConflicts(this)) return;
    JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC = myJavaDocPanel.getPolicy();
    final PsiClass superClass = getSuperClass();
    String name = superClass.getQualifiedName();
    if (name != null) {
      StatisticsManager.getInstance().incUseCount(new StatisticsInfo(PULL_UP_STATISTICS_KEY + myClass.getQualifiedName(), name));
    }

    List<GrMemberInfo> infos = getSelectedMemberInfos();
    GrPullUpHelper processor = new GrPullUpHelper(myClass, superClass, infos.toArray(new GrMemberInfo[infos.size()]), new DocCommentPolicy(getJavaDocPolicy()));
    invokeRefactoring(processor);
    close(OK_EXIT_CODE);
  }

  @Override
  protected void addCustomElementsToCentralPanel(JPanel panel) {
    myJavaDocPanel = new DocCommentPanel(RefactoringBundle.message("javadoc.for.abstracts"));
    myJavaDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    boolean hasJavadoc = false;
    for (GrMemberInfo info : myMemberInfos) {
      final PsiMember member = info.getMember();
      if (myMemberInfoModel.isAbstractEnabled(info) &&
          member instanceof PsiDocCommentOwner &&
          ((PsiDocCommentOwner)member).getDocComment() != null) {
        hasJavadoc = true;
        break;
      }
    }
    UIUtil.setEnabled(myJavaDocPanel, hasJavadoc, true);
    panel.add(myJavaDocPanel, BorderLayout.EAST);
  }

  @Override
  protected AbstractMemberSelectionTable<GrMember, GrMemberInfo> createMemberSelectionTable(List<GrMemberInfo> infos) {
    return new GrMemberSelectionTable(infos, RefactoringBundle.message("make.abstract"));
  }

  @Override
  protected MemberInfoModel<GrMember, GrMemberInfo> createMemberInfoModel() {
    //return new UsedByDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo>(myClass);
    return new MyMemberInfoModel(myClass, getSuperClass(), false);
  }

  private static class MyMemberInfoModel extends AbstractUsesDependencyMemberInfoModel<GrMember, PsiClass, GrMemberInfo> {
    public MyMemberInfoModel(PsiClass aClass, PsiClass superClass, boolean recursive) {
      super(aClass, superClass, recursive);
    }

    @Override
    protected int doCheck(@NotNull GrMemberInfo memberInfo, int problem) {
      if (problem == ERROR && memberInfo.isStatic()) {
        return WARNING;
      }
      return problem;
    }
  }
}
