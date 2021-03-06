// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.memberPullUp;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.classMembers.GrMemberInfo;
import org.jetbrains.plugins.groovy.refactoring.classMembers.GrMemberInfoStorage;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

/**
 * @author Max Medvedev
 */
public class GrPullUpHandler implements RefactoringActionHandler, GrPullUpDialog.Callback, ElementsHandler {
  private static final Logger LOG = Logger.getInstance(GrPullUpHandler.class);

  private PsiClass mySubclass;
  private Project myProject;

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);

    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(RefactoringBundle.message("the.caret.should.be.positioned.inside.a.class.to.pull.members.from"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MEMBERS_PULL_UP);
        return;
      }


      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, element)) return;


      if (element instanceof GrTypeDefinition || element instanceof GrField || element instanceof GrMethod) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }

      element = element.getParent();
    }
  }

  @Override
  public void invoke(@NotNull final Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    myProject = project;

    PsiElement element = elements[0];

    if (element instanceof GrTypeDefinition) {
      GrTypeDefinition aClass = (GrTypeDefinition)element;
      invokeImpl(project, dataContext, aClass, null);
    }
    else if (element instanceof GrMethod || element instanceof GrField) {
      GrTypeDefinition aClass = (GrTypeDefinition)((GrMember)element).getContainingClass();
      invokeImpl(project, dataContext, aClass, element);
    }
  }

  private void invokeImpl(Project project, DataContext dataContext, GrTypeDefinition aClass, PsiElement aMember) {
    final Editor editor = dataContext != null ? CommonDataKeys.EDITOR.getData(dataContext) : null;
    if (aClass == null) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("is.not.supported.in.the.current.context",
                                                                             getRefactoringName()));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MEMBERS_PULL_UP);
      return;
    }


    ArrayList<PsiClass> bases = RefactoringHierarchyUtil.createBasesList(aClass, false, true);

    if (bases.isEmpty()) {
      final GrTypeDefinition containingClass = (GrTypeDefinition)aClass.getContainingClass();
      if (containingClass != null) {
        invokeImpl(project, dataContext, containingClass, aClass);
        return;
      }

      String message = RefactoringBundle.getCannotRefactorMessage(
        RefactoringBundle.message("class.does.not.have.base.classes.interfaces.in.current.project", aClass.getQualifiedName()));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.MEMBERS_PULL_UP);
      return;
    }


    mySubclass = aClass;
    GrMemberInfoStorage memberInfoStorage = new GrMemberInfoStorage((GrTypeDefinition)mySubclass, new MemberInfoBase.Filter<>() {
      @Override
      public boolean includeMember(GrMember element) {
        return true;
      }
    });
    List<GrMemberInfo> members = memberInfoStorage.getClassMemberInfos(mySubclass);
    PsiManager manager = mySubclass.getManager();

    for (GrMemberInfo member : members) {
      if (manager.areElementsEquivalent(member.getMember(), aMember)) {
        member.setChecked(true);
        break;
      }
    }


    final GrPullUpDialog dialog = new GrPullUpDialog(project, aClass, bases, memberInfoStorage, this);
    dialog.show();
  }

  @Override
  public boolean checkConflicts(final GrPullUpDialog dialog) {
    /*                         todo */
    List<GrMemberInfo> _infos = dialog.getSelectedMemberInfos();
    final GrMemberInfo[] infos = _infos.toArray(new GrMemberInfo[0]);
    final PsiClass superClass = dialog.getSuperClass();
    if (!checkWritable(superClass, infos)) return false;
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
      final PsiDirectory targetDirectory = superClass.getContainingFile().getContainingDirectory();
      final PsiPackage targetPackage =
        targetDirectory != null ? JavaDirectoryService.getInstance().getPackage(targetDirectory) : null;
      conflicts.putAllValues(GrPullUpConflictsUtil.checkConflicts(infos, mySubclass, superClass, targetPackage, targetDirectory,
                                                                  dialog.getContainmentVerifier()));
    }), RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) {
      return false;
    }
    if (!conflicts.isEmpty()) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
      conflictsDialog.show();
      final boolean ok = conflictsDialog.isOK();
      if (!ok && conflictsDialog.isShowConflicts()) dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
      return ok;
    }

    return true;
  }

  private boolean checkWritable(PsiClass superClass, GrMemberInfo[] infos) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, superClass)) return false;
    for (GrMemberInfo info : infos) {
      if (info.getMember() instanceof PsiClass && info.getOverrides() != null) continue;
      if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, info.getMember())) return false;
    }

    return true;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && elements[0] instanceof PsiClass;
  }

  public static @Nls(capitalization = Title) String getRefactoringName() {
    return RefactoringBundle.message("pull.members.up.title");
  }
}
