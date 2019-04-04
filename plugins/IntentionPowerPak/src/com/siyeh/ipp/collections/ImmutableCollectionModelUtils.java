// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.collections;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.siyeh.ipp.collections.ImmutableCollectionModelUtils.ImmutableCollectionModel.CollectionType;

class ImmutableCollectionModelUtils {

  private static final Map<CollectionType, String> INITIALIZERS = new EnumMap<>(CollectionType.class);

  static {
    INITIALIZERS.put(CollectionType.SET, "new " + CommonClassNames.JAVA_UTIL_HASH_SET + "<>()");
    INITIALIZERS.put(CollectionType.MAP, "new " + CommonClassNames.JAVA_UTIL_HASH_MAP + "<>()");
    INITIALIZERS.put(CollectionType.LIST, "new " + CommonClassNames.JAVA_UTIL_ARRAY_LIST + "<>()");
  }

  @Nullable
  static ImmutableCollectionModel createModel(@NotNull PsiMethodCallExpression call) {
    CollectionType type = CollectionType.create(call);
    if (type == null) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (type == CollectionType.MAP && args.length % 2 != 0) return null;
    PsiVariable assignedVariable = getAssignedVariable(call);
    return new ImmutableCollectionModel(call, type, args, assignedVariable);
  }

  static void replaceWithMutable(@NotNull ImmutableCollectionModel model, @Nullable Editor editor) {
    PsiMethodCallExpression call = model.getCall();
    Project project = call.getProject();
    PsiElementFactory factory = PsiElementFactory.getInstance(project);
    String initializerText = INITIALIZERS.get(model.getType());
    if (initializerText == null) return;

    PsiVariable assignedVariable = model.getAssignedVariable();
    if (assignedVariable != null) {
      String name = assignedVariable.getName();
      if (name == null) return;
      PsiElement initializer = PsiReplacementUtil.replaceExpressionAndShorten(call, initializerText, new CommentTracker());
      PsiStatement statement = getOuterStatement(initializer);
      if (statement == null) return;
      PsiElement anchor = addUpdates(name, model, statement, factory);
      if (editor != null) editor.getCaretModel().moveToOffset(anchor.getTextRange().getEndOffset());
    }
    else {
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      PsiType type = call.getType();
      if (type == null) return;
      String[] nameSuggestions = getNameSuggestions(call, type, codeStyleManager);
      if (nameSuggestions.length == 0) return;
      String name = nameSuggestions[0];
      PsiElement anchor = new CommentTracker().replaceAndRestoreComments(call, name);
      PsiStatement statement = getOuterStatement(anchor);
      if (statement == null) return;
      PsiDeclarationStatement declaration = addDeclaration(name, initializerText, type, statement, factory, codeStyleManager);
      if (declaration == null) return;
      if (anchor.getParent() instanceof PsiExpressionStatement) new CommentTracker().deleteAndRestoreComments(anchor);
      anchor = addUpdates(name, model, declaration, factory);
      if (editor != null) {
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
        PsiVariable variable = PsiTreeUtil.getChildOfType(declaration, PsiVariable.class);
        if (variable == null) return;
        new VariableRenamer(variable, editor, anchor).performInplaceRefactoring(new LinkedHashSet<>(Arrays.asList(nameSuggestions)));
      }
    }
  }

  @Nullable
  private static PsiStatement getOuterStatement(PsiElement element) {
    PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiLambdaExpression.class);
    if (!(parent instanceof PsiLambdaExpression)) return (PsiStatement)parent;
    PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)parent;
    PsiCodeBlock codeBlock = RefactoringUtil.expandExpressionLambdaToCodeBlock(lambdaExpression);
    return ControlFlowUtils.getFirstStatementInBlock(codeBlock);
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

  @NotNull
  private static PsiElement addUpdates(@NotNull String name,
                                       @NotNull ImmutableCollectionModel model,
                                       @NotNull PsiStatement anchor,
                                       @NotNull PsiElementFactory factory) {
    PsiExpression[] args = model.getArgs();
    for (int i = 0; i < args.length; i++) {
      if (model.getType() != CollectionType.MAP) {
        anchor = addUpdate(name + ".add(" + args[i].getText() + ");", anchor, factory);
        continue;
      }
      if (i % 2 != 0) {
        anchor = addUpdate(name + ".put(" + args[i - 1].getText() + ", " + args[i].getText() + ");", anchor, factory);
      }
    }
    return anchor;
  }

  @NotNull
  private static PsiStatement addUpdate(@NotNull String updateText, @NotNull PsiStatement anchor, @NotNull PsiElementFactory factory) {
    PsiStatement statement = factory.createStatementFromText(updateText, null);
    return BlockUtils.addAfter(anchor, statement);
  }

  @Nullable
  private static PsiDeclarationStatement addDeclaration(@NotNull String name,
                                                        @NotNull String initializerText,
                                                        @NotNull PsiType type,
                                                        @NotNull PsiStatement statement,
                                                        @NotNull PsiElementFactory factory,
                                                        @NotNull JavaCodeStyleManager codeStyleManager) {
    PsiExpression initializer = factory.createExpressionFromText(initializerText, null);
    PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(name, type, initializer);
    declaration = ObjectUtils.tryCast(codeStyleManager.shortenClassReferences(declaration), PsiDeclarationStatement.class);
    if (declaration == null) return null;
    return ObjectUtils.tryCast(BlockUtils.addBefore(statement, declaration), PsiDeclarationStatement.class);
  }

  @NotNull
  private static String[] getNameSuggestions(@NotNull PsiMethodCallExpression call,
                                             @NotNull PsiType type,
                                             @NotNull JavaCodeStyleManager codeStyleManager) {
    String propertyName = getPropertyName(call, type, codeStyleManager);
    SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, propertyName, call, type);
    return codeStyleManager.suggestUniqueVariableName(nameInfo, call, true).names;
  }

  @NotNull
  private static String getPropertyName(PsiMethodCallExpression call, PsiType type, JavaCodeStyleManager codeStyleManager) {
    String propertyName = getPropertyNameByCall(call);
    if (propertyName != null) return propertyName;
    return codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type).names[0];
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
  }

  /**
   * Represents immutable collection creation call (e.g. {@link Collections#singleton(Object)}).
   */
  static class ImmutableCollectionModel {

    private static final CallMatcher LIST_CALL_MATCHER = CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "emptyList").parameterCount(0),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singletonList").parameterCount(1),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_LIST, "of").withLanguageLevelAtLeast(LanguageLevel.JDK_1_9)
    );
    private static final CallMatcher MAP_CALL_MATCHER = CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "emptyMap").parameterCount(0),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singletonMap").parameterCount(2),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_MAP, "of").withLanguageLevelAtLeast(LanguageLevel.JDK_1_9)
    );
    private static final CallMatcher SET_CALL_MATCHER = CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "emptySet").parameterCount(0),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singleton").parameterCount(1),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_SET, "of").withLanguageLevelAtLeast(LanguageLevel.JDK_1_9)
    );

    private final PsiMethodCallExpression myCall;
    private final CollectionType myType;
    private final PsiExpression[] myArgs;
    private final PsiVariable myAssignedVariable;

    @Contract(pure = true)
    ImmutableCollectionModel(@NotNull PsiMethodCallExpression call,
                             @NotNull CollectionType type,
                             @NotNull PsiExpression[] args,
                             @Nullable PsiVariable assignedVariable) {
      myCall = call;
      myType = type;
      myArgs = args;
      myAssignedVariable = assignedVariable;
    }

    PsiMethodCallExpression getCall() {
      return myCall;
    }

    CollectionType getType() {
      return myType;
    }

    PsiExpression[] getArgs() {
      return myArgs;
    }

    PsiVariable getAssignedVariable() {
      return myAssignedVariable;
    }

    enum CollectionType {

      MAP(MAP_CALL_MATCHER),
      LIST(LIST_CALL_MATCHER),
      SET(SET_CALL_MATCHER);

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
  }
}
