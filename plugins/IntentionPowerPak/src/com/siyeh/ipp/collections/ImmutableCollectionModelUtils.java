// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.collections;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

class ImmutableCollectionModelUtils {

  private static final CallMatcher MAP_ENTRY_CALL = staticCall(JAVA_UTIL_MAP, "entry").parameterCount(2);

  @Nullable
  static ImmutableCollectionModel createModel(@NotNull PsiMethodCallExpression call) {
    CollectionType type = CollectionType.create(call);
    if (type == null) return null;
    if (!ControlFlowUtils.canExtractStatement(call)) return null;
    String assignedVariable = getAssignedVariable(call);
    PsiExpression[] args = call.getArgumentList().getExpressions();
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiClassType classType = ObjectUtils.tryCast(call.getType(), PsiClassType.class);
    if (classType == null) return null;
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(call.getProject()).getResolveHelper();
    boolean hasNonResolvedTypeParams = Arrays.stream(classType.getParameters())
      .map(PsiUtil::resolveClassInClassTypeOnly)
      .anyMatch(aClass -> isNonResolvedTypeParameter(aClass, call, resolveHelper));
    if (hasNonResolvedTypeParams) return null;
    if ("ofEntries".equals(method.getName()) && Arrays.stream(args).anyMatch(arg -> extractPutArgs(arg) == null)) return null;
    return new ImmutableCollectionModel(call, type, method, assignedVariable);
  }

  @Contract("null, _, _ -> false")
  private static boolean isNonResolvedTypeParameter(@Nullable PsiClass parameter,
                                                    @NotNull PsiElement context,
                                                    @NotNull PsiResolveHelper resolveHelper) {
    if (!(parameter instanceof PsiTypeParameter)) return false;
    PsiTypeParameter typeParameter = (PsiTypeParameter)parameter;
    String name = typeParameter.getName();
    return name == null || resolveHelper.resolveReferencedClass(name, context) != parameter;
  }

  static void replaceWithMutable(@NotNull ImmutableCollectionModel model, @Nullable Editor editor) {
    ToMutableCollectionConverter.convert(model, editor);
  }

  @Nullable
  private static String getAssignedVariable(@NotNull PsiMethodCallExpression call) {
    PsiElement parent = PsiTreeUtil.getParentOfType(call, PsiVariable.class, PsiAssignmentExpression.class);
    if (parent == null) return null;
    if (parent instanceof PsiVariable) {
      PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(((PsiVariable)parent).getInitializer());
      return initializer == call ? ((PsiVariable)parent).getName() : null;
    }
    PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
    PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(assignment.getRExpression());
    if (rhs != call) return null;
    PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
    PsiReferenceExpression ref = ObjectUtils.tryCast(lhs, PsiReferenceExpression.class);
    if (ref == null) return null;
    PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier != null && SideEffectChecker.mayHaveSideEffects(qualifier)) return null;
    PsiVariable variable = ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
    if (variable == null || variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.VOLATILE)) return null;
    return ref.getText();
  }

  @Nullable
  private static String extractPutArgs(@NotNull PsiExpression entryExpression) {
    if (entryExpression instanceof PsiReferenceExpression) {
      return MessageFormat.format("{0}.getKey(), {0}.getValue()", entryExpression.getText());
    }
    PsiCallExpression call = ObjectUtils.tryCast(entryExpression, PsiCallExpression.class);
    if (call == null || !isEntryConstruction(call)) return null;
    PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList == null) return null;
    PsiExpression[] expressions = argumentList.getExpressions();
    if (expressions.length == 1) return extractPutArgs(expressions[0]);
    if (expressions.length == 2) return expressions[0].getText() + "," + expressions[1].getText();
    return null;
  }

  private static boolean isEntryConstruction(@NotNull PsiCallExpression call) {
    if (MAP_ENTRY_CALL.matches(call)) return true;
    PsiNewExpression newExpression = ObjectUtils.tryCast(call, PsiNewExpression.class);
    return newExpression != null && InheritanceUtil.isInheritor(newExpression.getType(), JAVA_UTIL_MAP_ENTRY);
  }

  private enum CollectionType {
    MAP(JAVA_UTIL_HASH_MAP), LIST(JAVA_UTIL_ARRAY_LIST), SET(JAVA_UTIL_HASH_SET);

    private final String myMutableClass;

    private static final CallMapper<CollectionType> MAPPER = new CallMapper<CollectionType>()
      .register(anyOf(
        staticCall(JAVA_UTIL_COLLECTIONS, "emptyMap").parameterCount(0),
        staticCall(JAVA_UTIL_COLLECTIONS, "singletonMap").parameterCount(2),
        staticCall(JAVA_UTIL_MAP, "of"),
        staticCall(JAVA_UTIL_MAP, "ofEntries"),
        staticCall("com.google.common.collect.ImmutableMap", "of")), MAP)
      .register(anyOf(
        staticCall(JAVA_UTIL_COLLECTIONS, "emptyList").parameterCount(0),
        staticCall(JAVA_UTIL_COLLECTIONS, "singletonList").parameterCount(1),
        staticCall(JAVA_UTIL_LIST, "of"),
        staticCall("com.google.common.collect.ImmutableList", "of")), LIST)
      .register(anyOf(
        staticCall(JAVA_UTIL_COLLECTIONS, "emptySet").parameterCount(0),
        staticCall(JAVA_UTIL_COLLECTIONS, "singleton").parameterCount(1),
        staticCall(JAVA_UTIL_SET, "of"),
        staticCall("com.google.common.collect.ImmutableSet", "of")), SET);

    CollectionType(String className) {
      myMutableClass = className;
    }

    @NotNull
    String getInitializerText(@Nullable String copyFrom) {
      return String.format("new " + myMutableClass + "<>(%s)", StringUtil.notNullize(copyFrom));
    }

    @Nullable
    static CollectionType create(@NotNull PsiMethodCallExpression call) {
      CollectionType type = MAPPER.mapFirst(call);
      if (type == null) return null;
      PsiType expectedType = ExpectedTypeUtils.findExpectedType(call, false);
      if (expectedType == null) return null;
      PsiClassType newType = TypeUtils.getType(type.myMutableClass, call);
      return expectedType.isAssignableFrom(newType) ? type : null;
    }
  }

  /**
   * Replaces immutable collection creation with mutable one.
   */
  private static class ToMutableCollectionConverter {

    private final PsiElementFactory myElementFactory;
    private final JavaCodeStyleManager myCodeStyleManager;
    private final Editor myEditor;

    private ToMutableCollectionConverter(@NotNull Project project, @Nullable Editor editor) {
      myElementFactory = PsiElementFactory.getInstance(project);
      myCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      myEditor = editor;
    }

    private void replaceWithMutable(@NotNull ImmutableCollectionModel model) {
      PsiMethodCallExpression call = RefactoringUtil.ensureCodeBlock(model.myCall);
      if (call == null) return;
      PsiStatement statement = ObjectUtils.tryCast(RefactoringUtil.getParentStatement(call, false), PsiStatement.class);
      if (statement == null) return;

      String assignedVariable = model.myAssignedVariable;
      if (assignedVariable != null) {
        String initializerText = model.myType.getInitializerText(model.myIsVarArgCall ? null : model.myCall.getText());
        PsiReplacementUtil.replaceExpressionAndShorten(call, initializerText, new CommentTracker());
        PsiElement anchor = addUpdates(assignedVariable, model, statement);
        if (myEditor != null) myEditor.getCaretModel().moveToOffset(anchor.getTextRange().getEndOffset());
      }
      else {
        createVariable(call, statement, model);
      }
    }

    private void createVariable(@NotNull PsiMethodCallExpression call,
                                @NotNull PsiStatement statement,
                                @NotNull ImmutableCollectionModel model) {
      PsiType type = call.getType();
      if (type == null) return;
      String[] names = getNameSuggestions(call, type);
      if (names.length == 0) return;
      String name = names[0];
      PsiDeclarationStatement declaration = createDeclaration(name, type, call, model, statement);
      if (declaration == null) return;
      PsiVariable declaredVariable = (PsiVariable)declaration.getDeclaredElements()[0];
      PsiElement anchor = addUpdates(name, model, declaration);
      if (call.getParent() instanceof PsiExpressionStatement) {
        new CommentTracker().deleteAndRestoreComments(statement);
      }
      else {
        PsiReplacementUtil.replaceExpression(call, name, new CommentTracker());
      }
      if (myEditor != null) VariableRenamer.rename(declaredVariable, names, myEditor, anchor);
    }

    @Nullable
    private PsiDeclarationStatement createDeclaration(@NotNull String name,
                                                      @NotNull PsiType type,
                                                      @NotNull PsiMethodCallExpression call,
                                                      @NotNull ImmutableCollectionModel model,
                                                      @NotNull PsiStatement usage) {
      String initializerText = model.myType.getInitializerText(model.myIsVarArgCall ? null : model.myCall.getText());
      PsiExpression initializer = myElementFactory.createExpressionFromText(initializerText, null);
      PsiType rhsType = initializer.getType();
      if (rhsType == null) return null;
      if (!TypeUtils.areConvertible(type, rhsType)) {
        type = ExpectedTypeUtils.findExpectedType(call, false);
      }
      if (type == null) return null;
      PsiDeclarationStatement declaration = myElementFactory.createVariableDeclarationStatement(name, type, initializer);
      return ObjectUtils.tryCast(BlockUtils.addBefore(usage, declaration), PsiDeclarationStatement.class);
    }

    @NotNull
    private String[] getNameSuggestions(@NotNull PsiMethodCallExpression call, @NotNull PsiType type) {
      String propertyName = getPropertyName(call, type);
      SuggestedNameInfo nameInfo = myCodeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, propertyName, call, type);
      return myCodeStyleManager.suggestUniqueVariableName(nameInfo, call, true).names;
    }

    @NotNull
    private String getPropertyName(@NotNull PsiMethodCallExpression call, @NotNull PsiType type) {
      String propertyName = getPropertyNameByCall(call);
      if (propertyName != null) return propertyName;
      return myCodeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type).names[0];
    }

    @NotNull
    private PsiStatement addUpdates(@NotNull String variable, @NotNull ImmutableCollectionModel model, @NotNull PsiStatement anchor) {
      if (!model.myIsVarArgCall) return anchor;
      return StreamEx.of(createUpdates(variable, model))
        .map(update -> myElementFactory.createStatementFromText(update, null))
        .foldLeft(anchor, (acc, update) -> BlockUtils.addAfter(acc, update));
    }

    @NotNull
    private static List<String> createUpdates(@NotNull String name, @NotNull ImmutableCollectionModel model) {
      boolean isMapOfEntriesCall = "ofEntries".equals(model.myCall.getMethodExpression().getReferenceName());
      List<String> updates = new ArrayList<>();
      PsiExpression[] args = model.myArgs;
      for (int i = 0; i < args.length; i++) {
        PsiExpression arg = args[i];
        if (model.myType != CollectionType.MAP) {
          updates.add(String.format("%s.add(%s);", name, arg.getText()));
        }
        else if (isMapOfEntriesCall) {
          updates.add(String.format("%s.put(%s);", name, extractPutArgs(arg)));
        }
        else if (i % 2 != 0) {
          updates.add(String.format("%s.put(%s, %s);", name, args[i - 1].getText(), arg.getText()));
        }
      }
      return updates;
    }

    @Nullable
    private static String getPropertyNameByCall(@NotNull PsiMethodCallExpression call) {
      PsiMethodCallExpression outerCall = PsiTreeUtil.getParentOfType(call, PsiMethodCallExpression.class);
      if (outerCall == null) return null;
      PsiMethod method = outerCall.resolveMethod();
      if (method == null) return null;
      PsiExpression[] arguments = outerCall.getArgumentList().getExpressions();
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 0) return null;
      for (int i = 0; i < arguments.length; i++) {
        if (arguments[i] == call) {
          int idx = i >= parameters.length ? parameters.length - 1 : i;
          return parameters[idx].getName();
        }
      }
      return null;
    }

    static void convert(@NotNull ImmutableCollectionModel model, @Nullable Editor editor) {
      new ToMutableCollectionConverter(model.myCall.getProject(), editor).replaceWithMutable(model);
    }
  }

  /**
   * Represents immutable collection creation call (e.g. {@link Collections#singleton(Object)}).
   */
  static class ImmutableCollectionModel {

    private final PsiMethodCallExpression myCall;
    private final CollectionType myType;
    private final boolean myIsVarArgCall;
    private final PsiExpression[] myArgs;
    private final String myAssignedVariable;

    @Contract(pure = true)
    ImmutableCollectionModel(@NotNull PsiMethodCallExpression call,
                             @NotNull CollectionType type,
                             @NotNull PsiMethod method,
                             @Nullable String assignedVariable) {
      myCall = call;
      myType = type;
      myIsVarArgCall = !method.isVarArgs() || MethodCallUtils.isVarArgCall(call);
      myArgs = call.getArgumentList().getExpressions();
      myAssignedVariable = assignedVariable;
    }
  }

  /**
   * Renames given variable and moves caret to anchor after renaming.
   */
  private static class VariableRenamer extends VariableInplaceRenamer {

    private final PsiElement myAnchor;

    private VariableRenamer(@NotNull PsiNamedElement elementToRename, @NotNull Editor editor, @NotNull PsiElement anchor) {
      super(elementToRename, editor);
      this.myAnchor = anchor;
      editor.getCaretModel().moveToOffset(elementToRename.getTextOffset());
    }

    @Override
    public void finish(boolean success) {
      super.finish(success);
      myEditor.getCaretModel().moveToOffset(myAnchor.getTextRange().getEndOffset());
    }

    static void rename(@NotNull PsiNamedElement elementToRename,
                       @NotNull String[] names,
                       @NotNull Editor editor,
                       @NotNull PsiElement anchor) {
      PsiDocumentManager.getInstance(elementToRename.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
      LinkedHashSet<String> suggestions = new LinkedHashSet<>(Arrays.asList(names));
      new VariableRenamer(elementToRename, editor, anchor).performInplaceRefactoring(suggestions);
    }
  }
}
