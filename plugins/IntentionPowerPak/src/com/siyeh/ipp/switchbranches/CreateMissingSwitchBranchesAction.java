// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.switchbranches;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.magicConstant.MagicConstantUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CreateSwitchBranchesUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class CreateMissingSwitchBranchesAction extends PsiElementBaseIntentionAction {
  private static final int MAX_NUMBER_OF_BRANCHES = 100;
  private List<Value> myAllValues;

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiSwitchBlock block = PsiTreeUtil.getParentOfType(element, PsiSwitchBlock.class, false, PsiCodeBlock.class, PsiStatement.class);
    if (block == null) return;
    if (block instanceof PsiSwitchExpression && !HighlightUtil.Feature.SWITCH_EXPRESSION.isAvailable(block)) {
      // Do not suggest if switch expression is not supported as we may generate unparseable code with 'yield' statement
      return;
    }
    List<Value> allValues = myAllValues;
    List<Value> missingValues = getMissingValues(block, allValues);
    if (missingValues.isEmpty()) return;
    List<String> allValueNames = ContainerUtil.map(allValues, v -> v.myName);
    List<String> missingValueNames = ContainerUtil.map(missingValues, v -> v.myName);
    List<PsiSwitchLabelStatementBase> addedLabels =
      CreateSwitchBranchesUtil.createMissingBranches(block, allValueNames, missingValueNames,
                                                           label -> extractConstantNames(allValues, label));
    CreateSwitchBranchesUtil.createTemplate(block, addedLabels);
  }

  private static List<String> extractConstantNames(List<Value> allValues, PsiSwitchLabelStatementBase label) {
    Set<Object> constants = getLabelConstants(label);
    return StreamEx.of(allValues).filter(v -> constants.contains(v.myValue)).map(v -> v.myName).toList();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiSwitchBlock block = PsiTreeUtil.getParentOfType(element, PsiSwitchBlock.class, false, PsiCodeBlock.class, PsiStatement.class);
    if (block == null) return false;
    PsiExpression expression = PsiUtil.skipParenthesizedExprDown(block.getExpression());
    if (expression == null) return false;
    PsiType type = expression.getType();
    if (type == null) return false;
    boolean isString = TypeUtils.isJavaLangString(type);
    if (type instanceof PsiClassType && !isString) return false;

    List<Value> values = getPossibleValues(expression);
    if (!values.isEmpty()) {
      List<Value> missingValues = getMissingValues(block, values);
      if (!missingValues.isEmpty()) {
        myAllValues = values;
        setText(CreateSwitchBranchesUtil.getActionName(ContainerUtil.map(missingValues, v -> v.myPresentationName)));
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static List<Value> getPossibleValues(PsiExpression expression) {
    CommonDataflow.DataflowResult dfr = CommonDataflow.getDataflowResult(expression);
    PsiType type = expression.getType();
    if (dfr != null) {
      LongRangeSet range = dfr.getExpressionFact(expression, DfaFactType.RANGE);
      if (range != null && !range.isCardinalityBigger(MAX_NUMBER_OF_BRANCHES)) {
        return range.stream().mapToObj(c -> Value.fromConstant(TypeConversionUtil.computeCastTo(c, type))).collect(Collectors.toList());
      }
      Set<Object> values = dfr.getExpressionValues(expression);
      if (!values.isEmpty() && values.size() <= MAX_NUMBER_OF_BRANCHES && values.stream().allMatch(v -> v instanceof String)) {
        return values.stream().map(Value::fromConstant).sorted(Comparator.comparing(v -> (String)v.myValue)).collect(Collectors.toList());
      }
    }
    if (expression instanceof PsiReferenceExpression) {
      PsiModifierListOwner target = ObjectUtils.tryCast(((PsiReferenceExpression)expression).resolve(), PsiModifierListOwner.class);
      if (target != null) {
        MagicConstantUtils.AllowedValues values = MagicConstantUtils.getAllowedValues(target, type);
        if (values != null && !values.isFlagSet() && values.getValues().length <= MAX_NUMBER_OF_BRANCHES) {
          List<Value> result = new ArrayList<>();
          for (PsiAnnotationMemberValue value : values.getValues()) {
            Value val = null;
            if (value instanceof PsiReferenceExpression) {
              PsiField field = ObjectUtils.tryCast(((PsiReferenceExpression)value).resolve(), PsiField.class);
              if (field != null) {
                val = Value.fromField(field);
              }
            }
            if (val == null) return Collections.emptyList();
            result.add(val);
          }
          return result;
        }
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private static List<Value> getMissingValues(PsiSwitchBlock block, List<Value> allValues) {
    List<Value> missingValues = new ArrayList<>(allValues);
    PsiCodeBlock body = block.getBody();
    if (body != null) {
      Collection<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.findChildrenOfType(body, PsiSwitchLabelStatementBase.class);
      Set<Object> existingBranches = StreamEx.of(labels)
        .toFlatCollection(CreateMissingSwitchBranchesAction::getLabelConstants, HashSet::new);
      missingValues.removeIf(v -> existingBranches.contains(v.myValue));
    }
    return missingValues;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Create missing switch branches";
  }

  @NotNull
  private static Set<Object> getLabelConstants(@NotNull PsiSwitchLabelStatementBase label) {
    final PsiExpressionList list = label.getCaseValues();
    if (list == null) {
      return Collections.emptySet();
    }
    Set<Object> constants = new HashSet<>();
    for (PsiExpression value : list.getExpressions()) {
      Object constant = ExpressionUtils.computeConstantExpression(value);
      if (constant instanceof Byte || constant instanceof Short) {
        constants.add(((Number)constant).intValue());
      }
      else if (constant instanceof String || constant instanceof Integer) {
        constants.add(constant);
      }
      else if (constant instanceof Character) {
        constants.add((int)(char)constant);
      } else {
        return Collections.emptySet();
      }
    }
    return constants;
  }

  private static class Value {
    final @NotNull String myName;
    final @NotNull String myPresentationName;
    final @NotNull Object myValue;

    Value(@NotNull String name, @NotNull String presentationName, @NotNull Object value) {
      myName = name;
      myPresentationName = presentationName;
      myValue = value;
    }

    @NotNull
    static Value fromConstant(Object value) {
      Object normalized = value;
      if (value instanceof Byte || value instanceof Short) {
        normalized = ((Number)value).intValue();
      }
      else if (value instanceof Character) {
        normalized = (int)(Character)value;
      }
      if (normalized instanceof Integer || normalized instanceof String) {
        String presentation = getPresentation(value);
        if (presentation != null) {
          return new Value(presentation, presentation, normalized);
        }
      }
      throw new IllegalArgumentException("Unexpected constant supplied: " + value);
    }

    @Nullable
    static Value fromField(@NotNull PsiField field) {
      String name = field.getName();
      PsiClass aClass = field.getContainingClass();
      if (aClass == null) return null;
      String className = aClass.getQualifiedName();
      if (className == null) return null;
      Object value = field.computeConstantValue();
      if (value == null) return null;
      return new Value(className + "." + field.getName(), name, value);
    }

    @Nullable
    static String getPresentation(Object constant) {
      if (constant instanceof Integer || constant instanceof Byte || constant instanceof Short) {
        return constant.toString();
      }
      if (constant instanceof String) {
        return '"' + StringUtil.escapeStringCharacters((String)constant) + '"';
      }
      if (constant instanceof Character) {
        return "'" + StringUtil.escapeCharCharacters(constant.toString()) + "'";
      }
      return null;
    }
  }
}
