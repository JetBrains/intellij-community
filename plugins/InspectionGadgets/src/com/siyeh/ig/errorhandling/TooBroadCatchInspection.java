/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.ExceptionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

import static com.intellij.codeInspection.options.OptPane.*;

public class TooBroadCatchInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean onlyWarnOnRootExceptions = false;
  @SuppressWarnings({"PublicField", "UnusedDeclaration"})
  public boolean ignoreInTestCode = false; // keep for compatibility
  @SuppressWarnings("PublicField")
  public boolean ignoreThrown = false;

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    final PsiElement context = (PsiElement)infos[1];
    final SmartTypePointerManager pointerManager = SmartTypePointerManager.getInstance(context.getProject());
    final List<PsiType> maskedTypes = (List<PsiType>)infos[0];
    final List<InspectionGadgetsFix> fixes = new ArrayList<>();
    for (PsiType thrown : maskedTypes) {
      final String typeText = thrown.getCanonicalText();
      if (CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION.equals(typeText)) {
        fixes.add(new ReplaceWithRuntimeExceptionFix());
      }
      else {
        fixes.add(new AddCatchSectionFix(pointerManager.createSmartTypePointer(thrown), typeText));
      }
    }
    final InspectionGadgetsFix fix = SuppressForTestsScopeFix.build(this, context);
    if (fix != null) {
      fixes.add(fix);
    }
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyWarnOnRootExceptions", InspectionGadgetsBundle.message("too.broad.catch.option")),
      checkbox("ignoreThrown", InspectionGadgetsBundle.message("overly.broad.throws.clause.ignore.thrown.option")));
  }

  @Override
  @NotNull
  public String getID() {
    return "OverlyBroadCatchBlock";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final List<PsiType> typesMasked = (List<PsiType>)infos[0];
    String typesMaskedString = typesMasked.get(0).getPresentableText();
    if (typesMasked.size() == 1) {
      return InspectionGadgetsBundle.message("too.broad.catch.problem.descriptor", typesMaskedString);
    }
    else {
      //Collections.sort(typesMasked);
      final int lastTypeIndex = typesMasked.size() - 1;
      for (int i = 1; i < lastTypeIndex; i++) {
        typesMaskedString += ", ";
        typesMaskedString += typesMasked.get(i).getPresentableText();
      }
      final String lastTypeString = typesMasked.get(lastTypeIndex).getPresentableText();
      return InspectionGadgetsBundle.message("too.broad.catch.problem.descriptor1", typesMaskedString, lastTypeString);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TooBroadCatchVisitor();
  }

  private static class ReplaceWithRuntimeExceptionFix extends InspectionGadgetsFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.catch.clause.for.runtime.exception.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiTypeElement typeElement)) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiClassType type = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION);
      final PsiTypeElement newTypeElement = factory.createTypeElement(type);
      typeElement.replace(newTypeElement);
    }
  }

  private static class AddCatchSectionFix extends InspectionGadgetsFix {
    @SafeFieldForPreview
    private final SmartTypePointer myThrown;
    private final String myText;

    AddCatchSectionFix(SmartTypePointer thrown, String typeText) {
      myThrown = thrown;
      myText = typeText;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("too.broad.catch.quickfix", myText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("add.catch.section.fix.family.name");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiType thrownType = myThrown.getType();
      if (thrownType == null) {
        return;
      }
      final PsiElement typeElement = descriptor.getPsiElement();
      if (typeElement == null) {
        return;
      }
      final PsiElement catchParameter = typeElement.getParent();
      if (!(catchParameter instanceof PsiParameter)) {
        return;
      }
      final PsiElement catchBlock = ((PsiParameter)catchParameter).getDeclarationScope();
      if (!(catchBlock instanceof PsiCatchSection myBeforeCatchSection)) {
        return;
      }
      final PsiTryStatement myTryStatement = myBeforeCatchSection.getTryStatement();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final String name = codeStyleManager.suggestUniqueVariableName("e", myTryStatement.getTryBlock(), false);
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiCatchSection section = factory.createCatchSection(thrownType, name, myTryStatement);
      final PsiCatchSection element = (PsiCatchSection)myTryStatement.addBefore(section, myBeforeCatchSection);
      codeStyleManager.shortenClassReferences(element);

      if (isOnTheFly()) {
        final PsiCodeBlock newBlock = element.getCatchBlock();
        assert newBlock != null;
        final TextRange range = SurroundWithUtil.getRangeToSelect(newBlock);
        final PsiFile file = element.getContainingFile();
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
          return;
        }
        final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (editor.getDocument() != document) {
          return;
        }
        editor.getCaretModel().moveToOffset(range.getStartOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      }
    }
  }

  private class TooBroadCatchVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      super.visitTryStatement(statement);
      final PsiCodeBlock tryBlock = statement.getTryBlock();
      if (tryBlock == null) {
        return;
      }
      final Set<PsiClassType> thrownTypes = ExceptionUtils.calculateExceptionsThrown(tryBlock);
      ExceptionUtils.calculateExceptionsThrown(statement.getResourceList(), thrownTypes);
      final Set<PsiType> caughtTypes = new HashSet<>(thrownTypes.size());
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      boolean runtimeExceptionSeen = false;
      for (final PsiCatchSection catchSection : catchSections) {
        final PsiParameter parameter = catchSection.getParameter();
        if (parameter == null) {
          continue;
        }
        final PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement == null) {
          continue;
        }
        final PsiTypeElement[] children = PsiTreeUtil.getChildrenOfType(typeElement, PsiTypeElement.class);
        if (children != null) {
          for (PsiTypeElement child : children) {
            runtimeExceptionSeen = check(thrownTypes, child, runtimeExceptionSeen, caughtTypes);
          }
        }
        else {
          runtimeExceptionSeen = check(thrownTypes, typeElement, runtimeExceptionSeen, caughtTypes);
        }
      }
    }

    private boolean check(Set<? extends PsiClassType> thrownTypes, PsiTypeElement caughtTypeElement, boolean runtimeExceptionSeen, Set<? super PsiType> caughtTypes) {
      final PsiType caughtType = caughtTypeElement.getType();
      if (CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION.equals(caughtType.getCanonicalText())) {
        runtimeExceptionSeen = true;
      }
      else if (thrownTypes.isEmpty() && CommonClassNames.JAVA_LANG_EXCEPTION.equals(caughtType.getCanonicalText())) {
        if (!runtimeExceptionSeen) {
          final PsiClassType runtimeExceptionType = TypeUtils.getType(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, caughtTypeElement);
          registerError(caughtTypeElement, Collections.singletonList(runtimeExceptionType), caughtTypeElement);
        }
      }
      final List<PsiType> maskedExceptions = findMaskedExceptions(thrownTypes, caughtType, caughtTypes);
      if (maskedExceptions.isEmpty()) {
        return runtimeExceptionSeen;
      }
      registerError(caughtTypeElement, maskedExceptions, caughtTypeElement);
      return runtimeExceptionSeen;
    }

    private List<PsiType> findMaskedExceptions(Set<? extends PsiClassType> thrownTypes, PsiType caughtType, Set<? super PsiType> caughtTypes) {
      if (thrownTypes.contains(caughtType)) {
        caughtTypes.add(caughtType);
        thrownTypes.remove(caughtType);
        if (ignoreThrown) {
          return Collections.emptyList();
        }
      }
      if (onlyWarnOnRootExceptions) {
        if (!ExceptionUtils.isGenericExceptionClass(caughtType)) {
          return Collections.emptyList();
        }
      }
      final List<PsiType> maskedTypes = new ArrayList<>();
      for (PsiType typeThrown : thrownTypes) {
        if (!caughtTypes.contains(typeThrown) && caughtType.isAssignableFrom(typeThrown)) {
          caughtTypes.add(typeThrown);
          maskedTypes.add(typeThrown);
        }
      }
      return maskedTypes;
    }
  }
}
