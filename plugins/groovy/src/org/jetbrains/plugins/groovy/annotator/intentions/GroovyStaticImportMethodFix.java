/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyStaticImportMethodFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.annotator.intentions.GroovyStaticImportMethodFix");
  private final SmartPsiElementPointer<GrCall> myMethodCall;
  private List<PsiMethod> myCandidates = null;

  public GroovyStaticImportMethodFix(@NotNull GrCall methodCallExpression) {
    myMethodCall = SmartPointerManager.getInstance(methodCallExpression.getProject()).createSmartPsiElementPointer(methodCallExpression);
  }

  @NotNull
  public String getText() {
    String text = "Static Import Method";
    if (getCandidates().size() == 1) {
      final int options = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_FQ_NAME;
      text += " '" + PsiFormatUtil.formatMethod(getCandidates().get(0), PsiSubstitutor.EMPTY, options, 0) + "'";
    }
    else {
      text += "...";
    }
    return text;
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Nullable
  private static GrReferenceExpression getMethodExpression(GrCall call) {
    GrExpression result = null;
    if (call instanceof GrMethodCallExpression) {
      result = ((GrMethodCallExpression)call).getInvokedExpression();
    }
    else if (call instanceof GrApplicationStatement) {
      result = ((GrApplicationStatement)call).getFunExpression();
    }

    return result instanceof GrReferenceExpression ? (GrReferenceExpression)result : null;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myMethodCall != null &&
           myMethodCall.getElement() != null &&
           myMethodCall.getElement().isValid() &&
           getMethodExpression(myMethodCall.getElement()) != null &&
           getMethodExpression(myMethodCall.getElement()).getQualifierExpression() == null &&
           file.getManager().isInProject(file) &&
           file.getManager().isInProject(file) &&
           !getCandidates().isEmpty();
  }

  @NotNull
  private List<PsiMethod> getMethodsToImport() {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myMethodCall.getProject());
    PsiShortNamesCache cache = facade.getShortNamesCache();

    GrCall element = myMethodCall.getElement();
    LOG.assertTrue(element != null);
    GrReferenceExpression reference = getMethodExpression(element);
    LOG.assertTrue(reference != null);
    GrArgumentList argumentList = element.getArgumentList();
    String name = reference.getReferenceName();

    ArrayList<PsiMethod> list = new ArrayList<PsiMethod>();
    if (name == null) return list;
    GlobalSearchScope scope = element.getResolveScope();
    PsiMethod[] methods = cache.getMethodsByNameIfNotMoreThan(name, scope, 20);
    List<PsiMethod> applicableList = new ArrayList<PsiMethod>();
    for (PsiMethod method : methods) {
      ProgressManager.checkCanceled();
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && JavaCompletionUtil.isInExcludedPackage(aClass)) continue;
      if (!method.hasModifierProperty(PsiModifier.STATIC)) continue;
      PsiFile file = method.getContainingFile();
      if (file instanceof PsiClassOwner
          //do not show methods from default package
          && ((PsiClassOwner)file).getPackageName().length() != 0 && PsiUtil.isAccessible(element, method)) {
        list.add(method);
        if (PsiUtil.isApplicable(PsiUtil.getArgumentTypes(element, true), method, PsiSubstitutor.EMPTY, false, element)) {
          applicableList.add(method);
        }
      }
    }
    List<PsiMethod> result = applicableList.isEmpty() ? list : applicableList;
    Collections.sort(result, new PsiProximityComparator(argumentList));
    return result;
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    if (getCandidates().size() == 1) {
      final PsiMethod toImport = getCandidates().get(0);
      doImport(toImport);
    }
    else {
      chooseAndImport(editor);
    }
  }

  private void doImport(final PsiMethod toImport) {
    CommandProcessor.getInstance().executeCommand(toImport.getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              GrCall element = myMethodCall.getElement();
              if (element != null) {
                getMethodExpression(element).bindToElementViaStaticImport(toImport.getContainingClass());
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }

          }
        });

      }
    }, getText(), this);

  }

  private void chooseAndImport(Editor editor) {
    final JList list = new JBList(getCandidates().toArray(new PsiMethod[getCandidates().size()]));
    list.setCellRenderer(new MethodCellRenderer(true));
    new PopupChooserBuilder(list).
      setTitle(QuickFixBundle.message("static.import.method.choose.method.to.import")).
      setMovable(true).
      setItemChoosenCallback(new Runnable() {
        public void run() {
          PsiMethod selectedValue = (PsiMethod)list.getSelectedValue();
          if (selectedValue == null) return;
          LOG.assertTrue(selectedValue.isValid());
          doImport(selectedValue);
        }
      }).createPopup().
      showInBestPositionFor(editor);
  }

  public boolean startInWriteAction() {
    return true;
  }

  private List<PsiMethod> getCandidates() {
    if (myCandidates == null) {
      myCandidates = getMethodsToImport();
    }
    return myCandidates;
  }
}
