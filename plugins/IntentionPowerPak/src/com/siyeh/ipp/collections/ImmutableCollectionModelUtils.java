// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.collections;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

class ImmutableCollectionModelUtils {

  private static final CallMatcher MAP_ENTRY_CALL = CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_MAP, "entry").parameterCount(2);

  @Nullable
  static ImmutableCollectionModel createModel(@NotNull PsiMethodCallExpression call) {
    CollectionType type = CollectionType.create(call);
    if (type == null) return null;
    if (!ControlFlowUtils.canExtractStatement(call)) return null;
    PsiVariable assignedVariable = getAssignedVariable(call);
    PsiExpression[] args = call.getArgumentList().getExpressions();
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    if (method.isVarArgs() && !MethodCallUtils.isVarArgCall(call)) {
      return args.length == 1 ? new ImmutableCollectionModel(call, type, true, args, assignedVariable) : null;
    }
    if ("ofEntries".equals(method.getName()) && Arrays.stream(args).anyMatch(arg -> extractPutArgs(arg) == null)) return null;
    return new ImmutableCollectionModel(call, type, false, args, assignedVariable);
  }

  static void replaceWithMutable(@NotNull ImmutableCollectionModel model, @Nullable Editor editor) {
    ToMutableCollectionConverter.convert(model, editor);
  }

  @Nullable
  private static PsiVariable getAssignedVariable(@NotNull PsiMethodCallExpression call) {
    PsiElement parent = PsiTreeUtil.getParentOfType(call, PsiVariable.class, PsiAssignmentExpression.class);
    if (parent == null) return null;
    if (parent instanceof PsiVariable) {
      PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(((PsiVariable)parent).getInitializer());
      return initializer == call ? (PsiVariable)parent : null;
    }
    PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
    PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(assignment.getRExpression());
    if (rhs != call) return null;
    PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
    PsiReferenceExpression ref = ObjectUtils.tryCast(lhs, PsiReferenceExpression.class);
    if (ref == null) return null;
    return ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
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
    return newExpression != null && InheritanceUtil.isInheritor(newExpression.getType(), CommonClassNames.JAVA_UTIL_MAP_ENTRY);
  }

  private enum CollectionType {

    MAP(CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "emptyMap").parameterCount(0),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singletonMap").parameterCount(2),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_MAP, "of")
        .withContextFilter(
          e -> e instanceof PsiMethodCallExpression && ((PsiMethodCallExpression)e).getArgumentList().getExpressionCount() % 2 == 0),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_MAP, "ofEntries"))),
    LIST(CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "emptyList").parameterCount(0),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singletonList").parameterCount(1),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_LIST, "of"))),
    SET(CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "emptySet").parameterCount(0),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singleton").parameterCount(1),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_SET, "of")));

    private final CallMatcher myMatcher;

    @Contract(pure = true)
    CollectionType(@NotNull CallMatcher matcher) {
      myMatcher = matcher;
    }

    @Nullable
    static CollectionType create(@NotNull PsiMethodCallExpression call) {
      return Arrays.stream(CollectionType.values()).filter(type -> type.myMatcher.test(call)).findFirst().orElse(null);
    }
  }

  /**
   * Replaces immutable collection creation with mutable one.
   */
  private static class ToMutableCollectionConverter {

    private static final Map<CollectionType, String> INITIALIZERS = new EnumMap<>(CollectionType.class);

    static {
      INITIALIZERS.put(CollectionType.SET, "new " + CommonClassNames.JAVA_UTIL_HASH_SET + "<>(%s)");
      INITIALIZERS.put(CollectionType.MAP, "new " + CommonClassNames.JAVA_UTIL_HASH_MAP + "<>(%s)");
      INITIALIZERS.put(CollectionType.LIST, "new " + CommonClassNames.JAVA_UTIL_ARRAY_LIST + "<>(%s)");
    }

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

      PsiVariable assignedVariable = model.myAssignedVariable;
      if (assignedVariable != null) {
        String initializerText = getInitializerText(model);
        if (initializerText == null) return;
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
      PsiDeclarationStatement declaration = createDeclaration(name, type, model, statement);
      if (declaration == null) return;
      PsiVariable declaredVariable = (PsiVariable)declaration.getDeclaredElements()[0];
      PsiElement anchor = addUpdates(declaredVariable, model, declaration);
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
                                                      @NotNull ImmutableCollectionModel model,
                                                      @NotNull PsiStatement usage) {
      String initializerText = getInitializerText(model);
      if (initializerText == null) return null;
      PsiExpression initializer = myElementFactory.createExpressionFromText(initializerText, null);
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
    private PsiStatement addUpdates(@NotNull PsiVariable variable, @NotNull ImmutableCollectionModel model, @NotNull PsiStatement anchor) {
      if (model.myIsNonVarArgCall) return anchor;
      String name = variable.getName();
      if (name == null) return anchor;
      return StreamEx.of(createUpdates(name, model))
        .map(update -> myElementFactory.createStatementFromText(update, null))
        .foldLeft(anchor, (acc, update) -> BlockUtils.addAfter(acc, update));
    }

    @Nullable
    private static String getInitializerText(@NotNull ImmutableCollectionModel model) {
      String initializerText = INITIALIZERS.get(model.myType);
      if (initializerText == null) return null;
      if (!model.myIsNonVarArgCall) return String.format(initializerText, "");
      if (model.myArgs.length != 1) return null;
      return String.format(initializerText, model.myCall.getText());
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
    private final boolean myIsNonVarArgCall;
    private final PsiExpression[] myArgs;
    private final PsiVariable myAssignedVariable;

    @Contract(pure = true)
    ImmutableCollectionModel(@NotNull PsiMethodCallExpression call,
                             @NotNull CollectionType type,
                             boolean isNonVarArgCall,
                             @NotNull PsiExpression[] args,
                             @Nullable PsiVariable assignedVariable) {
      myCall = call;
      myType = type;
      myIsNonVarArgCall = isNonVarArgCall;
      myArgs = args;
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
