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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.extract.GroovyExtractChooser;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.java2groovy.GroovyIntroduceParameterMethodUsagesProcessor;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler;
import org.jetbrains.plugins.groovy.refactoring.ui.MethodOrClosureScopeChooser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.groovy.refactoring.HelpID.GROOVY_INTRODUCE_PARAMETER;
import static org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase.createRange;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterHandler implements RefactoringActionHandler, MethodOrClosureScopeChooser.JBPopupOwner {
  static final String REFACTORING_NAME = RefactoringBundle.message("introduce.parameter.title");
  private JBPopup myEnclosingMethodsPopup;

  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file, final @Nullable DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();

      final List<GrExpression> expressions = GrIntroduceHandlerBase.collectExpressions(file, editor, offset, false);
      if (expressions.isEmpty()) {
        final GrVariable variable = GrIntroduceHandlerBase.findVariableAtCaret(file, editor, offset);
        if (variable == null || variable instanceof GrField || variable instanceof GrParameter) {
          selectionModel.selectLineAtCaret();
        }
        else {
          final TextRange textRange = variable.getTextRange();
          selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
        }
      }
      else if (expressions.size() == 1) {
        final TextRange textRange = expressions.get(0).getTextRange();
        selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
      }
      else {
        IntroduceTargetChooser.showChooser(editor, expressions, new Pass<GrExpression>() {
          public void pass(final GrExpression selectedValue) {
            invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(), selectedValue.getTextRange().getEndOffset());
          }
        }, new Function<GrExpression, String>() {
          @Override
          public String fun(GrExpression grExpression) {
            return grExpression.getText();
          }
        }
        );
        return;
      }
    }
    invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
  }

  private void invoke(final Project project, final Editor editor, PsiFile file, int startOffset, int endOffset) {
    try {
      final InitialInfo initialInfo = GroovyExtractChooser.invoke(project, editor, file, startOffset, endOffset, false);
      chooseScopeAndRun(initialInfo, editor);
    }
    catch (GrRefactoringError e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) throw e;
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), RefactoringBundle.message("introduce.parameter.title"), GROOVY_INTRODUCE_PARAMETER);
    }
  }

  private void chooseScopeAndRun(@NotNull final InitialInfo initialInfo, @NotNull final Editor editor) {
    final List<GrParametersOwner> scopes = findScopes(initialInfo);

    if (scopes.size() == 0) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("there.is.no.method.or.closure"));
    }
    else if (scopes.size() == 1 || ApplicationManager.getApplication().isUnitTestMode()) {
      final GrParametersOwner owner = scopes.get(0);
      final PsiElement toSearchFor;
      if (owner instanceof GrMethod) {
        toSearchFor = SuperMethodWarningUtil.checkSuperMethod((PsiMethod)owner, RefactoringBundle.message("to.refactor"));
        if (toSearchFor == null) return; //if it is null, refactoring was canceled
      }
      else {
        toSearchFor = MethodOrClosureScopeChooser.findVariableToUse(owner);
      }
      showDialogOrStartInplace(new IntroduceParameterInfoImpl(initialInfo, owner, toSearchFor), editor);
    }
    else {
      myEnclosingMethodsPopup = MethodOrClosureScopeChooser.create(scopes, editor, this, new PairFunction<GrParametersOwner, PsiElement, Object>() {
        @Override
        public Object fun(GrParametersOwner owner, PsiElement element) {
          showDialogOrStartInplace(new IntroduceParameterInfoImpl(initialInfo, owner, element), editor);
          return null;
        }
      });
      myEnclosingMethodsPopup.showInBestPositionFor(editor);
    }
  }

  private List<GrParametersOwner> findScopes(InitialInfo initialInfo) {
    PsiElement place = initialInfo.getContext();
    final List<GrParametersOwner> scopes = new ArrayList<GrParametersOwner>();
    while (true) {
      final GrParametersOwner parent = PsiTreeUtil.getParentOfType(place, GrMethod.class, GrClosableBlock.class);
      if (parent == null) break;
      scopes.add(parent);
      place = parent;
    }
    return scopes;
  }

  @Override
  public JBPopup get() {
    return myEnclosingMethodsPopup;
  }


  //method to hack in tests
  protected void showDialogOrStartInplace(final IntroduceParameterInfo info, final Editor editor) {
    if (isInplace(info, editor)) {
      final GrIntroduceContext context = createContext(info, editor);
      Map<OccurrencesChooser.ReplaceChoice, List<Object>> occurrencesMap = GrIntroduceHandlerBase.fillChoice(context);
      new OccurrencesChooser<Object>(editor) {
        @Override
        protected TextRange getOccurrenceRange(Object occurrence) {
          if (occurrence instanceof PsiElement) {
            return ((PsiElement)occurrence).getTextRange();
          }
          else if (occurrence instanceof StringPartInfo) {
            return ((StringPartInfo)occurrence).getRange();
          }
          else {
            return null;
          }
        }
      }.showChooser(new Pass<OccurrencesChooser.ReplaceChoice>() {
        @Override
        public void pass(OccurrencesChooser.ReplaceChoice choice) {
          startInplace(info, context);
        }
      }, occurrencesMap);
    }
    else {
      showDialog(info);
    }
  }

  protected void showDialog(IntroduceParameterInfo info) {
    new GrIntroduceParameterDialog(info).show();
  }

  private void startInplace(final IntroduceParameterInfo info, final GrIntroduceContext context) {
    final GrIntroduceParameterSettings settings = getSettingsForInplace(info, context);
    if (settings == null) return;

    CommandProcessor.getInstance().executeCommand(info.getProject(), new Runnable() {
      public void run() {
        Document document = context.getEditor().getDocument();

        List<RangeMarker> occurrences = ContainerUtil.newArrayList();
        if (settings.replaceAllOccurrences()) {
          for (PsiElement element : context.getOccurrences()) {
            occurrences.add(createRange(document, element));
          }
        }
        else if (context.getExpression() != null) {
          occurrences.add(createRange(document, context.getExpression()));
        }

        GrExpressionWrapper expr = new GrExpressionWrapper(GroovyIntroduceParameterUtil.findExpr(settings));

        SmartPsiElementPointer<GrParameter> pointer =
          ApplicationManager.getApplication().runWriteAction(new Computable<SmartPsiElementPointer<GrParameter>>() {
            @Override
            public SmartPsiElementPointer<GrParameter> compute() {
              Project project = context.getProject();
              GrParametersOwner toReplaceIn = info.getToReplaceIn();
              String name = GrInplaceParameterIntroducer.suggestNames(context, toReplaceIn).iterator().next();
              PsiType type = getType(context.getExpression(), context.getVar(), context.getStringPart());
              GrParameter parameter = GroovyIntroduceParameterMethodUsagesProcessor.addParameter(
                toReplaceIn, null,
                type != null ? type : PsiType.getJavaLangObject(PsiManager.getInstance(project), toReplaceIn.getResolveScope()), name,
                false, project);
              GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

              if (settings.replaceAllOccurrences()) {
                for (PsiElement element : context.getOccurrences()) {
                  element.replace(factory.createReferenceExpressionFromText(name));
                }
              }
              else {
                context.getExpression().replace(factory.createReferenceExpressionFromText(name));
              }
              return SmartPointerManager.getInstance(project).createSmartPsiElementPointer(parameter);
            }
          });
        GrVariable parameter = pointer != null ? pointer.getElement() : null;

        if (parameter != null) {
          GrInplaceIntroducer introducer = getIntroducer(parameter, context, settings, occurrences, expr);
          PsiDocumentManager.getInstance(info.getProject()).doPostponedOperationsAndUnblockDocument(context.getEditor().getDocument());
          introducer.performInplaceRefactoring(introducer.suggestNames(context));
        }
      }
    }, REFACTORING_NAME, REFACTORING_NAME);

  }

  private static GrInplaceIntroducer getIntroducer(GrVariable parameter,
                                                   GrIntroduceContext context,
                                                   GrIntroduceParameterSettings settings,
                                                   List<RangeMarker> occurrences,
                                                   GrExpressionWrapper expr) {
    //return new GrInplaceVariableIntroducer(parameter, context.getEditor(), context.getProject(), REFACTORING_NAME, occurrences, parameter);
    return new GrInplaceParameterIntroducer(parameter, context.getEditor(), context.getProject(), REFACTORING_NAME, occurrences, context.getPlace(), settings, expr);
  }

  private static GrIntroduceParameterSettings getSettingsForInplace(@NotNull IntroduceParameterInfo info, @NotNull GrIntroduceContext context) {
    GrExpression expr = context.getExpression();
    GrVariable var = context.getVar();

    TObjectIntHashMap<GrParameter> toRemove = GroovyIntroduceParameterUtil.findParametersToRemove(info);
    LinkedHashSet<String> names =
      GroovyIntroduceParameterUtil.suggestNames(var, expr, info.getStringPartInfo(), info.getToReplaceIn(), info.getProject());
    return new GrIntroduceExpressionSettingsImpl(info, names.iterator().next(), false, new TIntArrayList(toRemove.getValues()), false,
                                                 IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, expr, var,
                                                 getType(expr, var, info.getStringPartInfo()), false);
  }

  @Nullable
  private static PsiType getType(GrExpression expr, GrVariable var, StringPartInfo info) {
    if (expr != null) {
      return expr.getType();
    }
    else if (var != null) {
      return var.getDeclaredType();
    }
    else if (info != null) {
      return info.getLiteral().getType();
    }
    return null;
  }

  private static boolean isInplace(IntroduceParameterInfo info, Editor editor) {
    GrExpression expr = GroovyIntroduceParameterUtil.findExpr(info);
    GrVariable var = GroovyIntroduceParameterUtil.findVar(info);
    StringPartInfo stringPart = info.getStringPartInfo();

    return (expr != null || var != null || stringPart != null) && GrIntroduceHandlerBase.isInplace(editor, info.getContext());
  }


  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // Does nothing
  }

  private static GrIntroduceContext createContext(IntroduceParameterInfo info, Editor editor) {
    GrExpression expr = GroovyIntroduceParameterUtil.findExpr(info);
    GrVariable var = GroovyIntroduceParameterUtil.findVar(info);
    return new GrIntroduceVariableHandler().getContext(info.getProject(), editor, expr, var, info.getStringPartInfo(), info.getToReplaceIn());
  }
}
