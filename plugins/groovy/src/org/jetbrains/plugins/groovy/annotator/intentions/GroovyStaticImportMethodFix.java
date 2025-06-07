// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyStaticImportMethodFix extends Intention {
  private static final Logger LOG = Logger.getInstance(GroovyStaticImportMethodFix.class);
  private final SmartPsiElementPointer<GrMethodCall> myMethodCall;
  private List<PsiMethod> myCandidates = null;

  public GroovyStaticImportMethodFix(@NotNull GrMethodCall methodCallExpression) {
    myMethodCall = SmartPointerManager.getInstance(methodCallExpression.getProject()).createSmartPsiElementPointer(methodCallExpression);
  }

  @Override
  public @NotNull @IntentionName String getText() {
    if (getCandidates().size() == 1) {
      final int options = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_FQ_NAME;
      String methodText = PsiFormatUtil.formatMethod(getCandidates().get(0), PsiSubstitutor.EMPTY, options, 0);
      return GroovyBundle.message("static.import.method.0.fix", methodText);
    }
    else {
      return GroovyBundle.message("static.import.method.fix");
    }
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    List<PsiMethod> candidates = getCandidates();
    if (candidates.size() != 1) {
      return null;
    }
    GrMethodCall call = myMethodCall.getElement();
    if (call == null) {
      return null;
    }
    GrMethodCall copy = PsiTreeUtil.findSameElementInCopy(call, target);
    GroovyStaticImportMethodFix fix = new GroovyStaticImportMethodFix(copy);
    fix.myCandidates = candidates;
    return fix;
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  private @Nullable GrReferenceExpression getMethodExpression() {
    GrMethodCall methodCall = myMethodCall.getElement();
    if (methodCall == null) return null;
    return getMethodExpression(methodCall);
  }

  private static @Nullable GrReferenceExpression getMethodExpression(@NotNull GrMethodCall call) {
    GrExpression result = call.getInvokedExpression();
    return result instanceof GrReferenceExpression ? (GrReferenceExpression)result : null;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    myCandidates = null;

    if (!psiFile.getManager().isInProject(psiFile)) return false;

    GrReferenceExpression invokedExpression = getMethodExpression();
    if (invokedExpression == null || invokedExpression.getQualifierExpression() != null) return false;

    return !getCandidates().isEmpty();
  }

  private @NotNull List<PsiMethod> getMethodsToImport() {
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myMethodCall.getProject());

    GrMethodCall element = myMethodCall.getElement();
    LOG.assertTrue(element != null);
    GrReferenceExpression reference = getMethodExpression(element);
    LOG.assertTrue(reference != null);
    GrArgumentList argumentList = element.getArgumentList();
    String name = reference.getReferenceName();

    ArrayList<PsiMethod> list = new ArrayList<>();
    if (name == null) return list;
    GlobalSearchScope scope = element.getResolveScope();
    PsiMethod[] methods = cache.getMethodsByNameIfNotMoreThan(name, scope, 20);
    List<PsiMethod> applicableList = new ArrayList<>();
    for (PsiMethod method : methods) {
      ProgressManager.checkCanceled();
      if (JavaCompletionUtil.isInExcludedPackage(method, false)) continue;
      if (!method.hasModifierProperty(PsiModifier.STATIC)) continue;
      PsiFile file = method.getContainingFile();
      if (file instanceof PsiClassOwner
          //do not show methods from default package
          && !((PsiClassOwner)file).getPackageName().isEmpty() && PsiUtil.isAccessible(element, method)) {
        list.add(method);
        if (PsiUtil.isApplicable(PsiUtil.getArgumentTypes(element, true), method, PsiSubstitutor.EMPTY, element, false)) {
          applicableList.add(method);
        }
      }
    }
    List<PsiMethod> result = applicableList.isEmpty() ? list : applicableList;
    result.sort(new PsiProximityComparator(argumentList));
    return result;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    if (getCandidates().size() == 1) {
      final PsiMethod toImport = getCandidates().get(0);
      doImport(toImport);
    }
    else {
      chooseAndImport(editor);
    }
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return true;
      }
    };
  }

  private void doImport(final PsiMethod toImport) {
    CommandProcessor.getInstance().executeCommand(toImport.getProject(), () -> WriteAction.run(() -> {
      try {
        GrReferenceExpression expression = getMethodExpression();
        if (expression != null) {
          expression.bindToElementViaStaticImport(toImport);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }), getText(), this);
  }

  private void chooseAndImport(Editor editor) {
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(getCandidates())
      .setRenderer(new MethodCellRenderer(true))
      .setTitle(QuickFixBundle.message("static.import.method.choose.method.to.import"))
      .setMovable(true)
      .setItemChosenCallback((selectedValue) -> {
        LOG.assertTrue(selectedValue.isValid());
        doImport(selectedValue);
      })
      .createPopup()
      .showInBestPositionFor(editor);
  }

  private @NotNull List<PsiMethod> getCandidates() {
    List<PsiMethod> result = myCandidates;
    if (result == null) {
      result = getMethodsToImport();
      myCandidates = result;
    }
    return result;
  }
}
