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
package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.lang.Language;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Max Medvedev
 */
public class GroovyInlineLocalHandler extends InlineActionHandler {
  private static Logger LOG = Logger.getInstance(GroovyInlineLocalHandler.class);
  public static final String INLINE_VARIABLE = RefactoringBundle.message("inline.variable.title");

  @Override
  public boolean isEnabledForLanguage(Language l) {
    return GroovyFileType.GROOVY_LANGUAGE == l;
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    return element instanceof GrVariable && GroovyRefactoringUtil.isLocalVariable((GrVariable)element);
  }

  @Override
  public void inlineElement(final Project project, Editor editor, final PsiElement element) {
    invoke(project, editor, (GrVariable)element);
  }

  public static void invoke(final Project project, Editor editor, final GrVariable local) {
    final PsiReference invocationReference = editor != null ? TargetElementUtilBase.findReference(editor) : null;

    final InlineLocalVarSettings localVarSettings = createSettings(local, editor, invocationReference != null);
    if (localVarSettings == null) return;

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, local)) return;

    final Collection<? extends PsiReference> allReferences =
      localVarSettings.isOnlyOneReferenceToInline() ? Collections.singleton(invocationReference) : localVarSettings.getRefs();
    if (checkConflicts(project, allReferences)) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {

            final UsageInfo[] usages = new UsageInfo[allReferences.size()];
            int i = 0;
            for (PsiReference ref : allReferences) {
              usages[i++] = new UsageInfo(ref);
            }

            RefactoringUtil.sortDepthFirstRightLeftOrder(usages);

            for (UsageInfo usage : usages) {
              GrVariableInliner.inlineReference(usage, local, localVarSettings.getInitializer());
            }

            if (!localVarSettings.isOnlyOneReferenceToInline()) {
              removeDefinition(local, localVarSettings);
            }
          }
        }, RefactoringBundle.message("inline.command", local.getName()), null);
      }
    });
  }

  private static void removeDefinition(PsiElement element, InlineLocalVarSettings settings) {
    LOG.assertTrue(element instanceof GrVariable && GroovyRefactoringUtil.isLocalVariable((GrVariable)element), element.getClass());

    final GrExpression initializer = settings.getInitializer();
    final PsiElement parent = initializer.getParent();

    if (parent instanceof GrAssignmentExpression) {
      parent.delete();
      return;
    }

    if (parent instanceof GrVariable) {
      if (!settings.isRemoveDeclaration()) {
        initializer.delete();
        return;
      }
    }

    final PsiElement owner = element.getParent().getParent();
    if (owner instanceof GrVariableDeclarationOwner) {
      ((GrVariableDeclarationOwner)owner).removeVariable((GrVariable)element);
    }
    else {
      element.delete();
    }
  }

  private static boolean checkConflicts(Project project, Collection<? extends PsiReference> allReferences) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    for (PsiReference reference : allReferences) {
      collectConflicts(reference, conflicts);
    }
    if (!conflicts.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values());
      }
      else {
        final ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
        conflictsDialog.show();
        if (!conflictsDialog.isOK()) {
          return true;
        }
      }
    }
    return false;
  }

  private static void collectConflicts(final PsiReference reference, final MultiMap<PsiElement, String> conflicts) {
    GrExpression expr = (GrExpression)reference.getElement();
    if (expr.getParent() instanceof GrAssignmentExpression) {
      GrAssignmentExpression parent = (GrAssignmentExpression)expr.getParent();
      if (expr.equals(parent.getLValue())) {
        conflicts.putValue(expr, GroovyRefactoringBundle.message("local.varaible.is.lvalue"));
      }
    }
  }

  /**
   * Returns Settings object for referenced definition in case of local variable
   */
  @Nullable
  private static InlineLocalVarSettings createSettings(final GrVariable variable, Editor editor, boolean invokedOnReference) {
    final String localName = variable.getName();
    final Project project = variable.getProject();



    final Collection<PsiReference> refs = ReferencesSearch.search(variable).findAll();

    GrExpression initializer = null;

    Instruction writeInstr = null;

    final Instruction[] flow = ControlFlowUtils.findControlFlowOwner(variable).getControlFlow();
    final ArrayList<BitSet> writes = ControlFlowUtils.inferWriteAccessMap(flow, variable);

    GrReferenceExpression refExpr = null;
    if (invokedOnReference) {
      LOG.assertTrue(editor != null, "null editor but invokedOnReference==true");
      final PsiReference ref = TargetElementUtilBase.findReference(editor);
      LOG.assertTrue(ref != null);
      final PsiElement cur = ref.getElement();
      if (cur instanceof GrReferenceExpression) {
        refExpr = (GrReferenceExpression)cur;
        final Instruction instruction = ContainerUtil.find(flow, new Condition<Instruction>() {
          @Override
          public boolean value(Instruction instruction) {
            return instruction.getElement() == cur;
          }
        });

        LOG.assertTrue(instruction != null);
        final BitSet prev = writes.get(instruction.num());
        if (prev.cardinality() == 1) {
          writeInstr = flow[prev.nextSetBit(0)];
          final PsiElement element = writeInstr.getElement();
          if (element instanceof GrVariable) {
            initializer = ((GrVariable)element).getInitializerGroovy();
          }
          else if (element instanceof GrReferenceExpression) {
            initializer = PsiUtil.getInitializerFor((GrReferenceExpression)element);
          }
        }
      }
    }
    else  {
      initializer = variable.getInitializerGroovy();
      writeInstr = ContainerUtil.find(flow, new Condition<Instruction>() {
        @Override
        public boolean value(Instruction instruction) {
          return instruction.getElement() == variable;
        }
      });
    }

    if (initializer == null || writeInstr == null) {
      String message = GroovyRefactoringBundle.message("cannot.find.a.single.definition.to.inline.local.var");
      CommonRefactoringUtil.showErrorHint(variable.getProject(), editor, message, INLINE_VARIABLE, HelpID.INLINE_VARIABLE);
      return null;
    }

    ArrayList<GrReferenceExpression> toInline = new ArrayList<GrReferenceExpression>();
    for (Instruction instruction : flow) {
      if (!(instruction instanceof ReadWriteVariableInstruction)) continue;
      if (((ReadWriteVariableInstruction)instruction).isWrite()) continue;

      final PsiElement element = instruction.getElement();
      if (element instanceof GrVariable && element != variable) continue;
      if (!(element instanceof GrReferenceExpression)) continue;

      final GrReferenceExpression ref = (GrReferenceExpression)element;
      if (ref.isQualified() || ref.resolve() != variable) continue;

      final BitSet prev = writes.get(instruction.num());
      if (prev.cardinality() == 1 && prev.get(writeInstr.num())) {
        toInline.add(ref);
      }
    }

    if (toInline.size()==0) {
      CommonRefactoringUtil.showErrorHint(project, editor, GroovyRefactoringBundle.message("variable.is.never.used.0", localName), INLINE_VARIABLE, HelpID.INLINE_VARIABLE);
      return null;
    }

    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final TextAttributes writeAttributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);

    if (refExpr != null && PsiUtil.isAccessedForReading(refExpr) && !toInline.contains(refExpr)) {
      highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{refExpr}, attributes, true, null);
      String message = RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("variable.is.accessed.for.writing", localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, INLINE_VARIABLE, HelpID.INLINE_VARIABLE);
      setStatusBarInfo(project);
      return null;
    }

    for (GrReferenceExpression ref : toInline) {
      if (PsiUtil.isAccessedForWriting(ref)) {
        final String message = GroovyRefactoringBundle.message("variable.is.accessed.for.writing", localName);
        HighlightManager.getInstance(project).addOccurrenceHighlights(editor, new PsiElement[]{ref}, writeAttributes, true, null);
        CommonRefactoringUtil.showErrorHint(project, editor, message, INLINE_VARIABLE, HelpID.INLINE_VARIABLE);
        setStatusBarInfo(project);

        return null;
      }
    }

    highlightManager.addOccurrenceHighlights(editor, PsiUtilCore.toPsiElementArray(toInline), attributes, false, highlighters);
    return showDialogAndGetSettings(localName, project, toInline, initializer, toInline.size() == refs.size());
  }

  /**
   * Shows dialog with question to inline
   */
  @Nullable
  private static InlineLocalVarSettings showDialogAndGetSettings(String localName,
                                                                 Project project,
                                                                 Collection<GrReferenceExpression> refs,
                                                                 GrExpression initializer,
                                                                 boolean removeDeclaration) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new InlineLocalVarSettings(initializer, refs, removeDeclaration);
    }

    final String question = GroovyRefactoringBundle.message("inline.local.variable.prompt.0.1", localName, refs.size());
    RefactoringMessageDialog dialog =
      new RefactoringMessageDialog(INLINE_VARIABLE, question, HelpID.INLINE_VARIABLE, "OptionPane.questionIcon",
                                   true, project);
    dialog.show();
    if (dialog.isOK()) {
      return new InlineLocalVarSettings(initializer, refs, removeDeclaration);
    }

    setStatusBarInfo(project);
    setStatusBarInfo(project);
    return null;
  }

  private static void setStatusBarInfo(Project project) {
    WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
  }
}
