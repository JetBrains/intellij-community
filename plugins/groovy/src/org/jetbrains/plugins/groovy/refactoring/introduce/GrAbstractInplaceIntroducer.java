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
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.util.Function;
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


/**
 * Created by Max Medvedev on 10/28/13
 */
public abstract class GrAbstractInplaceIntroducer<Settings extends GrIntroduceSettings> extends AbstractInplaceIntroducer<GrVariable, PsiElement> {

  private SmartTypePointer myTypePointer;
  private final OccurrencesChooser.ReplaceChoice myReplaceChoice;

  private RangeMarker myVarMarker;
  private final PsiFile myFile;

  private final GrIntroduceContext myContext;

  public GrAbstractInplaceIntroducer(String title,
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
  public GrExpression restoreExpression(PsiFile containingFile, GrVariable variable, RangeMarker marker, String exprText) {
    if (exprText == null) return null;
    if (variable == null || !variable.isValid()) return null;
    final PsiElement refVariableElement = containingFile.findElementAt(marker.getStartOffset());
    final PsiElement refVariableElementParent = refVariableElement != null ? refVariableElement.getParent() : null;
    GrExpression expression =
      refVariableElementParent instanceof GrNewExpression && refVariableElement.getNode().getElementType() == GroovyTokenTypes.kNEW
      ? (GrNewExpression)refVariableElementParent
      : refVariableElementParent instanceof GrParenthesizedExpression ? ((GrParenthesizedExpression)refVariableElementParent).getOperand() 
                                                                      : PsiTreeUtil.getParentOfType(refVariableElement, GrReferenceExpression.class);
    if (expression instanceof GrReferenceExpression && !(expression.getParent() instanceof GrMethodCall)) {
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

  @Nullable
  @Override
  protected PsiElement getNameIdentifier() {
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
  protected void moveOffsetAfter(boolean success) {
    super.moveOffsetAfter(success);

  }

  @Override
  protected void performIntroduce() {
    runRefactoring(new IntroduceContextAdapter(), getSettings(), true);
  }

  @NotNull
  protected PsiElement[] restoreOccurrences() {
    List<PsiElement> result = ContainerUtil.map(getOccurrenceMarkers(), new Function<RangeMarker, PsiElement>() {
      @Override
      public PsiElement fun(RangeMarker marker) {
        return PsiImplUtil.findElementInRange(myFile, marker.getStartOffset(), marker.getEndOffset(), GrExpression.class);
      }
    });
    return PsiUtilCore.toPsiElementArray(result);
  }

  @Nullable
  @Override
  protected GrVariable createFieldToStartTemplateOn(boolean replaceAll, String[] names) {

    final Settings settings = getInitialSettingsForInplace(myContext, myReplaceChoice, names);
    if (settings == null) return null;

    SmartPsiElementPointer<GrVariable> pointer = ApplicationManager.getApplication().runWriteAction(new Computable<SmartPsiElementPointer<GrVariable>>() {
      @Override
      public SmartPsiElementPointer<GrVariable> compute() {
        GrVariable var = runRefactoring(myContext, settings, false);
        return var != null ? SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(var) : null;
      }
    });

    if (pointer != null) {
      GrVariable var = pointer.getElement();
      if (var != null) {
        myVarMarker = myContext.getEditor().getDocument().createRangeMarker(var.getTextRange());
      }
      return var;
    }
    else {
      return null;
    }
  }

  protected abstract GrVariable runRefactoring(GrIntroduceContext context, Settings settings, boolean processUsages);

  @Nullable
  protected abstract Settings getInitialSettingsForInplace(@NotNull GrIntroduceContext context,
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

  @Nullable
  protected PsiType getSelectedType() {
    return myTypePointer != null ? myTypePointer.getType() : null;
  }

  private class IntroduceContextAdapter implements GrIntroduceContext {
    @NotNull
    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
    public Editor getEditor() {
      return myEditor;
    }

    @Nullable
    @Override
    public GrExpression getExpression() {
      return (GrExpression)getExpr();
    }

    @Nullable
    @Override
    public GrVariable getVar() {
      return getLocalVariable();
    }

    @Nullable
    @Override
    public StringPartInfo getStringPart() {
      return null;
    }

    @NotNull
    @Override
    public PsiElement[] getOccurrences() {
      return restoreOccurrences();
    }

    @Override
    public PsiElement getScope() {
      return myScope;
    }

    @NotNull
    @Override
    public PsiElement getPlace() {
      GrExpression expression = getExpression();
      return expression != null ? expression : getLocalVariable();
    }
  }
}
