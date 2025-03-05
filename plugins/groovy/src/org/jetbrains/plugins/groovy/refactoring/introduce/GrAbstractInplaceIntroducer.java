// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.List;


public abstract class GrAbstractInplaceIntroducer<Settings extends GrIntroduceSettings> extends AbstractInplaceIntroducer<GrVariable, PsiElement> {

  private SmartTypePointer myTypePointer;
  private final OccurrencesChooser.ReplaceChoice myReplaceChoice;

  private RangeMarker myVarMarker;
  private final PsiFile myFile;

  private final GrIntroduceContext myContext;

  public GrAbstractInplaceIntroducer(@NlsContexts.Command String title,
                                     OccurrencesChooser.ReplaceChoice replaceChoice,
                                     GrIntroduceContext context) {
    super(context.getProject(), context.getEditor(), context.getExpression(), context.getVar(), context.getOccurrences(), title, GroovyFileType.GROOVY_FILE_TYPE);
    myReplaceChoice = replaceChoice;
    myContext = context;
    myFile = context.getPlace().getContainingFile();
  }

  public GrIntroduceContext getContext() {
    return myContext;
  }

  @Override
  public void setReplaceAllOccurrences(boolean allOccurrences) {
    throw new IncorrectOperationException("don't invoke this method");
  }

  @Override
  public GrExpression restoreExpression(@NotNull PsiFile containingFile, @NotNull GrVariable variable, @NotNull RangeMarker marker, String exprText) {
    if (exprText == null) return null;
    if (!variable.isValid()) return null;
    final PsiElement refVariableElement = containingFile.findElementAt(marker.getStartOffset());
    final PsiElement refVariableElementParent = refVariableElement != null ? refVariableElement.getParent() : null;
    GrExpression expression =
      refVariableElementParent instanceof GrNewExpression && refVariableElement.getNode().getElementType() == GroovyTokenTypes.kNEW
      ? (GrNewExpression)refVariableElementParent
      : refVariableElementParent instanceof GrParenthesizedExpression ? ((GrParenthesizedExpression)refVariableElementParent).getOperand() 
                                                                      : PsiTreeUtil.getParentOfType(refVariableElement, GrReferenceExpression.class);
    if (expression instanceof GrReferenceExpression) {
      final String referenceName = ((GrReferenceExpression)expression).getReferenceName();
      if (((GrReferenceExpression)expression).resolve() == variable ||
          Comparing.strEqual(variable.getName(), referenceName) ||
          Comparing.strEqual(exprText, referenceName)) {
        return (GrExpression)expression
          .replace(GroovyPsiElementFactory.getInstance(myProject).createExpressionFromText(exprText, variable));
      }
    }
    if (expression == null) {
      expression = PsiTreeUtil.getParentOfType(refVariableElement, GrExpression.class);
    }
    while (expression instanceof GrReferenceExpression || expression instanceof GrCall) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof GrMethodCallExpression) {
        if (parent.getText().equals(exprText)) return (GrExpression)parent;
      }
      if (parent instanceof GrExpression) {
        expression = (GrExpression)parent;
        if (expression.getText().equals(exprText)) {
          return expression;
        }
      }
      else if (expression instanceof GrReferenceExpression){
        return null;
      } else {
        break;
      }
    }
    if (expression != null && expression.isValid() && expression.getText().equals(exprText)) {
      return expression;
    }

    if (refVariableElementParent instanceof GrExpression && refVariableElementParent.getText().equals(exprText)) {
      return (GrExpression)refVariableElementParent;
    }

    return null;
  }

  @Override
  protected void updateTitle(@Nullable GrVariable variable, String value) {
    if (variable == null) {
      super.updateTitle(variable, value);
    }
    else {
      final String variableText = variable.getParent().getText();
      final PsiElement identifier = variable.getNameIdentifierGroovy();
      final int startOffsetInParent = identifier.getStartOffsetInParent() + variable.getStartOffsetInParent();
      setPreviewText(
        variableText.substring(0, startOffsetInParent) + value + variableText.substring(startOffsetInParent + identifier.getTextLength()));
      revalidate();
    }
  }

  @Override
  protected void updateTitle(@Nullable GrVariable variable) {
    if (variable == null) return;
    setPreviewText(variable.getParent().getText());
    revalidate();
  }

  @Override
  protected @Nullable PsiElement getNameIdentifier() {
    return ((GrVariable)myElementToRename).getNameIdentifierGroovy();
  }

  @Override
  protected GrVariable getVariable() {
    if (myVarMarker == null) return null;

    int offset = myVarMarker.getStartOffset();
    PsiElement at = myFile.findElementAt(offset);
    GrVariable var = PsiTreeUtil.getParentOfType(at, GrVariable.class);
    return var;
  }

  @Override
  protected void performIntroduce() {
    runRefactoring(new IntroduceContextAdapter(), getSettings(), true);
  }

  protected PsiElement @NotNull [] restoreOccurrences() {
    List<PsiElement> result = ContainerUtil.map(getOccurrenceMarkers(), marker -> PsiImplUtil.findElementInRange(myFile, marker.getStartOffset(), marker.getEndOffset(), GrExpression.class));
    return PsiUtilCore.toPsiElementArray(result);
  }

  @Override
  protected @Nullable GrVariable createFieldToStartTemplateOn(boolean replaceAll, String @NotNull [] names) {

    final Settings settings = getInitialSettingsForInplace(myContext, myReplaceChoice, names);
    if (settings == null) return null;

    GrVariable var = runRefactoring(myContext, settings, false);
    if (var != null) {
      myVarMarker = myContext.getEditor().getDocument().createRangeMarker(var.getTextRange());
    }
    return var;
  }

  protected abstract GrVariable runRefactoring(GrIntroduceContext context, Settings settings, boolean processUsages);

  protected final GrVariable refactorInWriteAction(Computable<? extends GrVariable> computable) {
    SmartPsiElementPointer<GrVariable> pointer = WriteAction.compute(() -> {
      GrVariable var = computable.compute();
      return var != null ? SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(var) : null;
    });
    return pointer != null ? pointer.getElement() : null;
  }

  protected abstract @Nullable Settings getInitialSettingsForInplace(@NotNull GrIntroduceContext context,
                                                                     @NotNull OccurrencesChooser.ReplaceChoice choice,
                                                                     String[] names);

  @Override
  public boolean isReplaceAllOccurrences() {
    return myReplaceChoice != OccurrencesChooser.ReplaceChoice.NO;
  }

  protected abstract Settings getSettings();

  @Override
  protected void restoreState(@NotNull GrVariable psiField) {
    PsiType declaredType = psiField.getDeclaredType();
    myTypePointer = declaredType != null ? SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(declaredType) : null;
    super.restoreState(psiField);
  }

  protected @Nullable PsiType getSelectedType() {
    return myTypePointer != null ? myTypePointer.getType() : null;
  }

  private class IntroduceContextAdapter implements GrIntroduceContext {
    @Override
    public @NotNull Project getProject() {
      return myProject;
    }

    @Override
    public Editor getEditor() {
      return myEditor;
    }

    @Override
    public @Nullable GrExpression getExpression() {
      return (GrExpression)getExpr();
    }

    @Override
    public @Nullable GrVariable getVar() {
      return getLocalVariable();
    }

    @Override
    public @Nullable StringPartInfo getStringPart() {
      return null;
    }

    @Override
    public PsiElement @NotNull [] getOccurrences() {
      return restoreOccurrences();
    }

    @Override
    public PsiElement getScope() {
      return myScope;
    }

    @Override
    public @NotNull PsiElement getPlace() {
      GrExpression expression = getExpression();
      return expression != null ? expression : getLocalVariable();
    }
  }
}
