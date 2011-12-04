/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceRefactoringError;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.plugins.groovy.refactoring.HelpID.GROOVY_INTRODUCE_PARAMETER;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(GrIntroduceParameterHandler.class);

  @NonNls public static final String USE_SUPER_METHOD_OF = "Change base method";
  @NonNls public static final String CHANGE_USAGES_OF = "Change usages";
  private JBPopup myEnclosingMethodsPopup;

  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file, final @Nullable DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();

      final List<GrExpression> expressions = GrIntroduceHandlerBase.collectExpressions(file, editor, offset);
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
        final Pass<GrExpression> callback = new Pass<GrExpression>() {
          public void pass(final GrExpression selectedValue) {
            invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(), selectedValue.getTextRange().getEndOffset());
          }
        };
        final Function<GrExpression, String> renderer = new Function<GrExpression, String>() {
          @Override
          public String fun(GrExpression grExpression) {
            return grExpression.getText();
          }
        };
        IntroduceTargetChooser.showChooser(editor, expressions, callback, renderer
        );
        return;
      }
    }
    invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
  }

  private void invoke(final Project project, final Editor editor, PsiFile file, int startOffset, int endOffset) {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      if (!(file instanceof GroovyFileBase)) {
        throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("only.in.groovy.files"));
      }
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) {
        throw new GrIntroduceRefactoringError(RefactoringBundle.message("readonly.occurences.found"));
      }

      GrExpression selectedExpr = GrIntroduceHandlerBase.findExpression(file, startOffset, endOffset);
      final GrVariable variable = GrIntroduceHandlerBase.findVariable(file, startOffset, endOffset);
      if (variable == null && selectedExpr == null) {
        throw new GrIntroduceRefactoringError(null);
      }

      findScope(selectedExpr, variable, editor, project);
    }
    catch (GrIntroduceRefactoringError e) {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(e.getMessage()), RefactoringBundle.message("introduce.parameter.title"),
                                          GROOVY_INTRODUCE_PARAMETER);
    }
  }

  public void findScope(final GrExpression expression, final GrVariable variable, final Editor editor, final Project project) {
    PsiElement place = expression == null ? variable : expression;

    final List<GrParametersOwner> scopes = new ArrayList<GrParametersOwner>();
    while (true) {
      final GrParametersOwner parent = PsiTreeUtil.getParentOfType(place, GrMethod.class, GrClosableBlock.class);
      if (parent == null) break;
      scopes.add(parent);
      place = parent;
    }

    if (scopes.size() == 0) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("there.is.no.method.or.closure"));
    }
    else if (scopes.size() == 1) {
      final GrParametersOwner owner = scopes.get(0);
      if (owner instanceof GrMethod) {
        PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod((PsiMethod)owner, RefactoringBundle.message("to.refactor"));
        if (newMethod == null) return;
        getContext(project, editor, expression, variable, owner, newMethod);
        return;
      }
      else {
        getContext(project, editor, expression, variable, owner, findVariableToUse(owner));
        return;
      }
    }
    else {
      final JPanel panel = new JPanel(new BorderLayout());
      final JCheckBox superMethod = new JCheckBox(USE_SUPER_METHOD_OF, true);
      superMethod.setMnemonic('U');
      panel.add(superMethod, BorderLayout.SOUTH);
      final JBList list = new JBList(scopes.toArray());
      list.setVisibleRowCount(5);
      list.setCellRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

          final String text;
          if (value instanceof PsiMethod) {
            final PsiMethod method = (PsiMethod)value;
            text = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                              PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                              PsiFormatUtilBase.SHOW_NAME |
                                              PsiFormatUtilBase.SHOW_PARAMETERS,
                                              PsiFormatUtilBase.SHOW_TYPE);
            final int flags = Iconable.ICON_FLAG_VISIBILITY;
            final Icon icon = method.getIcon(flags);
            if (icon != null) setIcon(icon);
          }
          else {
            LOG.assertTrue(value instanceof GrClosableBlock);
            setIcon(GroovyIcons.GROOVY_ICON_16x16);
            text = "{...}";
          }
          setText(text);
          return this;
        }
      });
      list.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      list.setSelectedIndex(0);
      final List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
      final TextAttributes attributes =
        EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      list.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(final ListSelectionEvent e) {
          final GrParametersOwner selectedMethod = (GrParametersOwner)list.getSelectedValue();
          if (selectedMethod == null) return;
          dropHighlighters(highlighters);
          updateView(selectedMethod, editor, attributes, highlighters, superMethod);
        }
      });
      updateView(scopes.get(0), editor, attributes, highlighters, superMethod);
      final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(list);
      scrollPane.setBorder(null);
      panel.add(scrollPane, BorderLayout.CENTER);

      final List<Pair<ActionListener, KeyStroke>> keyboardActions = Collections.singletonList(Pair.<ActionListener, KeyStroke>create(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final GrParametersOwner ToSearchIn = (GrParametersOwner)list.getSelectedValue();
          if (myEnclosingMethodsPopup != null && myEnclosingMethodsPopup.isVisible()) {
            myEnclosingMethodsPopup.cancel();
          }


          final PsiElement toSearchFor;
          if (ToSearchIn instanceof GrMethod) {
            toSearchFor = superMethod.isEnabled() && superMethod.isSelected() ? ((GrMethod)ToSearchIn).findDeepestSuperMethod() : ((GrMethod)ToSearchIn);
          }
          else {
            toSearchFor = superMethod.isEnabled() && superMethod.isSelected() ? ToSearchIn.getParent(): null;
          }
          Runnable runnable = new Runnable() {
            public void run() {
              getContext(project, editor, expression, variable, ToSearchIn, toSearchFor);
            }
          };
          IdeFocusManager.findInstance().doWhenFocusSettlesDown(runnable);
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));
      myEnclosingMethodsPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, list)
        .setTitle("Introduce parameter to")
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setKeyboardActions(keyboardActions).addListener(new JBPopupAdapter() {
          @Override
          public void onClosed(LightweightWindowEvent event) {
            dropHighlighters(highlighters);
          }
        }).createPopup();
      myEnclosingMethodsPopup.showInBestPositionFor(editor);
    }
  }

  @Nullable
  private static GrVariable findVariableToUse(GrParametersOwner owner) {
    final PsiElement parent = owner.getParent();
    if (parent instanceof GrVariable) return (GrVariable)parent;
    if (parent instanceof GrAssignmentExpression &&
        ((GrAssignmentExpression)parent).getRValue() == owner &&
        ((GrAssignmentExpression)parent).getOperationToken() == GroovyTokenTypes.mASSIGN) {
      final GrExpression lValue = ((GrAssignmentExpression)parent).getLValue();
      if (lValue instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)lValue).resolve();
        if (resolved instanceof GrVariable) {
          return (GrVariable)resolved;
        }
      }
    }
    return null;
  }

  protected void getContext(Project project,
                            Editor editor,
                            GrExpression expression,
                            @Nullable GrVariable variable,
                            GrParametersOwner toReplaceIn,
                            @Nullable PsiElement toSearchFor) {
    GrIntroduceContext context;
    if (variable == null) {
      final PsiElement[] occurrences = findOccurrences(expression, toReplaceIn);
      context = new GrIntroduceContext(project, editor, expression, occurrences, toReplaceIn, variable);
    }
    else {
      final List<PsiElement> list = Collections.synchronizedList(new ArrayList<PsiElement>());
      ReferencesSearch.search(variable, new LocalSearchScope(toReplaceIn)).forEach(new Processor<PsiReference>() {
        @Override
        public boolean process(PsiReference psiReference) {
          final PsiElement element = psiReference.getElement();
          if (element != null) {
            list.add(element);
          }
          return true;
        }
      });
      context = new GrIntroduceContext(project, editor, variable.getInitializerGroovy(), list.toArray(new PsiElement[list.size()]), toReplaceIn, variable);
    }

    showDialog(new GrIntroduceParameterContext(context, toReplaceIn, toSearchFor));
  }


  protected void showDialog(GrIntroduceParameterContext context) {
    TObjectIntHashMap<GrParameter> toRemove = GroovyIntroduceParameterUtil.findParametersToRemove(context);
    final GrIntroduceDialog<GrIntroduceParameterSettings> dialog = new GrIntroduceParameterDialog(context, toRemove);
    dialog.show();
  }

  private static PsiElement[] findOccurrences(GrExpression expression, PsiElement scope) {
    final PsiElement[] occurrences = GroovyRefactoringUtil.getExpressionOccurrences(PsiUtil.skipParentheses(expression, false), scope, true);
    if (occurrences == null || occurrences.length == 0) {
      throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("no.occurences.found"));
    }
    return occurrences;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // Does nothing
  }

  private static void updateView(GrParametersOwner selectedMethod,
                                 Editor editor,
                                 TextAttributes attributes,
                                 List<RangeHighlighter> highlighters,
                                 JCheckBox superMethod) {
    final MarkupModel markupModel = editor.getMarkupModel();
    final TextRange textRange = selectedMethod.getTextRange();
    final RangeHighlighter rangeHighlighter =
      markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1, attributes, HighlighterTargetArea.EXACT_RANGE);
    highlighters.add(rangeHighlighter);
    if (selectedMethod instanceof GrMethod) {
      superMethod.setText(USE_SUPER_METHOD_OF);
      superMethod.setEnabled(((GrMethod)selectedMethod).findDeepestSuperMethod() != null);
    }
    else {
      superMethod.setText(CHANGE_USAGES_OF);
      superMethod.setEnabled(findVariableToUse(selectedMethod) != null);
    }
  }

  private static void dropHighlighters(List<RangeHighlighter> highlighters) {
    for (RangeHighlighter highlighter : highlighters) {
      highlighter.dispose();
    }
    highlighters.clear();
  }
}
