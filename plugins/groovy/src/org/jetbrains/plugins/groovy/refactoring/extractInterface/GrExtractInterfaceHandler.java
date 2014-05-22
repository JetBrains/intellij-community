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
package org.jetbrains.plugins.groovy.refactoring.extractInterface;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractInterface.ExtractClassUtil;
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassUtil;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import javax.swing.*;

/**
 * @author Max Medvedev
 */
public class GrExtractInterfaceHandler implements RefactoringActionHandler, ElementsHandler {
  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_INTERFACE);
        return;
      }

      if (element instanceof GrTypeDefinition && !(element instanceof GrAnonymousClassDefinition)) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }

      element = element.getParent();
    }
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;


    myProject = project;
    myClass = (PsiClass)elements[0];


    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, myClass)) return;

/* todo
    final JavaExtractSuperBaseDialog dialog = new ExtractInterfaceDialog(myProject, myClass);
    dialog.show();
    if (!dialog.isOK() || !dialog.isExtractSuperclass()) return;

    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    ExtractSuperClassUtil.checkSuperAccessible(dialog.getTargetDirectory(), conflicts, myClass);
    if (!ExtractSuperClassUtil.showConflicts(dialog, conflicts, myProject)) return;

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            myInterfaceName = dialog.getExtractedSuperName();
            mySelectedMembers = ArrayUtil.toObjectArray(dialog.getSelectedMemberInfos(), MemberInfo.class);
            myTargetDir = dialog.getTargetDirectory();
            myJavaDocPolicy = new DocCommentPolicy(dialog.getDocCommentPolicy());
            try {
              doRefactoring();
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, REFACTORING_NAME, null);*/
  }

  private void doRefactoring() throws IncorrectOperationException {
    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());
    final PsiClass anInterface;
    try {
      anInterface = extractInterface(myTargetDir, myClass, myInterfaceName, mySelectedMembers, myJavaDocPolicy);
    }
    finally {
      a.finish();
    }


    if (anInterface != null) {
      final SmartPsiElementPointer<PsiClass> classPointer =
        SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(myClass);
      final SmartPsiElementPointer<PsiClass> interfacePointer =
        SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(anInterface);
      final Runnable turnRefsToSuperRunnable = new Runnable() {
        @Override
        public void run() {
          ExtractClassUtil.askAndTurnRefsToSuper(myProject, classPointer, interfacePointer);
        }
      };
      SwingUtilities.invokeLater(turnRefsToSuperRunnable);
    }
  }

  public static PsiClass extractInterface(PsiDirectory targetDir,
                                          PsiClass aClass,
                                          String interfaceName,
                                          MemberInfo[] selectedMembers,
                                          DocCommentPolicy javaDocPolicy) throws IncorrectOperationException {
    PsiClass anInterface = JavaDirectoryService.getInstance().createInterface(targetDir, interfaceName);
    PsiJavaCodeReferenceElement ref = ExtractSuperClassUtil.createExtendingReference(anInterface, aClass, selectedMembers);
    final PsiReferenceList referenceList = aClass.isInterface() ? aClass.getExtendsList() : aClass.getImplementsList();
    assert referenceList != null;
    referenceList.add(ref);
    PullUpProcessor pullUpHelper = new PullUpProcessor(aClass, anInterface, selectedMembers, javaDocPolicy);
    pullUpHelper.moveMembersToBase();
    return anInterface;
  }

  private String getCommandName() {
    return RefactoringBundle.message("extract.interface.command.name", myInterfaceName, DescriptiveNameUtil.getDescriptiveName(myClass));
  }

  @Override
  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && elements[0] instanceof PsiClass;
  }

  private static final Logger LOG = Logger.getInstance(GrExtractInterfaceHandler.class);
  public static final String REFACTORING_NAME = RefactoringBundle.message("extract.interface.title");
  private Project myProject;
  private PsiClass myClass;
  private String myInterfaceName;
  private MemberInfo[] mySelectedMembers;
  private PsiDirectory myTargetDir;
  private DocCommentPolicy myJavaDocPolicy;
}
