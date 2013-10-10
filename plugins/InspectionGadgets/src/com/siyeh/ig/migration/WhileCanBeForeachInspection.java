/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.StringUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WhileCanBeForeachInspection extends WhileCanBeForeachInspectionBase {

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new WhileCanBeForeachFix();
  }

  private static class WhileCanBeForeachFix extends InspectionGadgetsFix {
     @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("foreach.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement whileElement = descriptor.getPsiElement();
      final PsiWhileStatement whileStatement = (PsiWhileStatement)whileElement.getParent();
      replaceWhileWithForEach(whileStatement);
    }

    private static void replaceWhileWithForEach(@NotNull PsiWhileStatement whileStatement) {
      final PsiStatement body = whileStatement.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement initialization = getPreviousStatement(whileStatement);
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
      if (declaration == null) {
        return;
      }
      final PsiElement declaredElement = declaration.getDeclaredElements()[0];
      if (!(declaredElement instanceof PsiLocalVariable)) {
        return;
      }
      final PsiLocalVariable iterator = (PsiLocalVariable)declaredElement;
      final PsiMethodCallExpression initializer = (PsiMethodCallExpression)iterator.getInitializer();
      if (initializer == null) {
        return;
      }
      final PsiReferenceExpression methodExpression = initializer.getMethodExpression();
      final PsiExpression collection = methodExpression.getQualifierExpression();
      final PsiType collectionType;
      if (collection == null) {
        final PsiClass aClass = PsiTreeUtil.getParentOfType(whileStatement, PsiClass.class);
        if (aClass == null) {
          return;
        }
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(whileStatement.getProject());
        collectionType = factory.createType(aClass);
      }
      else {
        collectionType = collection.getType();
      }
      if (collectionType == null) {
        return;
      }
      final PsiType contentType = getContentType(collectionType, CommonClassNames.JAVA_LANG_ITERABLE, whileStatement);
      if (contentType == null) {
        return;
      }
      final PsiType iteratorType = iterator.getType();
      final PsiType iteratorContentType = getContentType(iteratorType, "java.util.Iterator", whileStatement);
      if (iteratorContentType == null) {
        return;
      }
      final Project project = whileStatement.getProject();
      final PsiStatement firstStatement = getFirstStatement(body);
      final boolean isDeclaration = isIteratorNextDeclaration(firstStatement, iterator, contentType);
      final PsiStatement statementToSkip;
      @NonNls final String contentVariableName;
      if (isDeclaration) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        if (declarationStatement == null) {
          return;
        }
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        final PsiLocalVariable localVariable = (PsiLocalVariable)declaredElements[0];
        contentVariableName = localVariable.getName();
        statementToSkip = declarationStatement;
      }
      else {
        if (collection instanceof PsiReferenceExpression) {
          final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)collection;
          final String collectionName = referenceElement.getReferenceName();
          contentVariableName = createNewVariableName(whileStatement, iteratorContentType, collectionName);
        }
        else {
          contentVariableName = createNewVariableName(whileStatement, iteratorContentType, null);
        }
        statementToSkip = null;
      }
      @NonNls final StringBuilder out = new StringBuilder();
      out.append("for(");
      if (CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_PARAMETERS) {
        out.append("final ");
      }
      out.append(iteratorContentType.getCanonicalText()).append(' ').append(contentVariableName).append(": ");
      if (!TypeConversionUtil.isAssignable(iteratorContentType, contentType)) {
        out.append("(java.lang.Iterable<").append(iteratorContentType.getCanonicalText()).append(">)");
      }
      if (collection == null) {
        out.append("this");
      } else {
        out.append(collection.getText());
      }
      out.append(')');

      replaceIteratorNext(body, contentVariableName, iterator, contentType, statementToSkip, out);
      final Query<PsiReference> query = ReferencesSearch.search(iterator, iterator.getUseScope());
      boolean deleteIterator = true;
      for (PsiReference usage : query) {
        final PsiElement element = usage.getElement();
        if (PsiTreeUtil.isAncestor(whileStatement, element, true)) {
          continue;
        }
        final PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class);
        if (assignment == null) {
          // iterator is read after while loop,
          // so cannot be deleted
          deleteIterator = false;
          break;
        }
        final PsiExpression expression = assignment.getRExpression();
        initializer.delete();
        iterator.setInitializer(expression);
        final PsiElement statement = assignment.getParent();
        final PsiElement lastChild = statement.getLastChild();
        if (lastChild instanceof PsiComment) {
          iterator.add(lastChild);
        }
        statement.replace(iterator);
        break;
      }
      if (deleteIterator) {
        iterator.delete();
      }
      final String result = out.toString();
      replaceStatementAndShortenClassNames(whileStatement, result);
    }

    @Nullable
    private static PsiType getContentType(PsiType type, String containerClassName, PsiElement context) {
      if (!(type instanceof PsiClassType)) {
        return null;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      final Project project = context.getProject();

      if (aClass == null) {
        return null;
      }
      final PsiClass iterableClass = JavaPsiFacade.getInstance(project).findClass(containerClassName, aClass.getResolveScope());
      if (iterableClass == null) {
        return null;
      }
      final PsiSubstitutor substitutor1 = resolveResult.getSubstitutor();
      final PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(iterableClass, aClass, substitutor1);
      if (substitutor == null) {
        return null;
      }
      PsiType parameterType = substitutor.substitute(iterableClass.getTypeParameters()[0]);
      if (parameterType instanceof PsiCapturedWildcardType) {
        parameterType = ((PsiCapturedWildcardType)parameterType).getWildcard();
      }
      if (parameterType != null) {
        if (parameterType instanceof PsiWildcardType) {
          if (((PsiWildcardType)parameterType).isExtends()) {
            return ((PsiWildcardType)parameterType).getBound();
          }
          else {
            return null;
          }
        }
        return parameterType;
      }
      return TypeUtils.getObjectType(context);
    }

    private static void replaceIteratorNext(@NotNull PsiElement element, String contentVariableName, PsiVariable iterator,
                                            PsiType contentType, PsiElement childToSkip, StringBuilder out) {
      if (isIteratorNext(element, iterator, contentType)) {
        out.append(contentVariableName);
      }
      else {
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          out.append(element.getText());
        }
        else {
          boolean skippingWhiteSpace = false;
          for (final PsiElement child : children) {
            if (shouldSkip(iterator, contentType, child)) {
              skippingWhiteSpace = true;
            }
            else if (child.equals(childToSkip)) {
              skippingWhiteSpace = true;
            }
            else if (!(child instanceof PsiWhiteSpace) || !skippingWhiteSpace) {
              skippingWhiteSpace = false;
              replaceIteratorNext(child, contentVariableName, iterator, contentType, childToSkip, out);
            }
          }
        }
      }
    }

    private static boolean shouldSkip(PsiVariable iterator, PsiType contentType, PsiElement child) {
      if (!(child instanceof PsiExpressionStatement)) {
        return false;
      }
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)child;
      final PsiExpression expression = expressionStatement.getExpression();
      return isIteratorNext(expression, iterator, contentType);
    }

    private static boolean isIteratorNextDeclaration(PsiStatement statement, PsiVariable iterator, PsiType contentType) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        return false;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
      final PsiElement[] elements = declarationStatement.getDeclaredElements();
      if (elements.length != 1) {
        return false;
      }
      final PsiElement element = elements[0];
      if (!(element instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)element;
      final PsiExpression initializer = variable.getInitializer();
      return isIteratorNext(initializer, iterator, contentType);
    }

    private static boolean isIteratorNext(PsiElement element, PsiVariable iterator, PsiType contentType) {
      if (element == null) {
        return false;
      }
      if (element instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression castExpression = (PsiTypeCastExpression)element;
        final PsiType type = castExpression.getType();
        if (type == null) {
          return false;
        }
        if (!type.equals(contentType)) {
          return false;
        }
        final PsiExpression operand = castExpression.getOperand();
        return isIteratorNext(operand, iterator, contentType);
      }
      if (!(element instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)element;
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 0) {
        return false;
      }
      final PsiReferenceExpression reference = callExpression.getMethodExpression();
      @NonNls final String referenceName = reference.getReferenceName();
      if (!HardcodedMethodConstants.NEXT.equals(referenceName)) {
        return false;
      }
      final PsiExpression expression = reference.getQualifierExpression();
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      return iterator.equals(target);
    }

    private static String createNewVariableName(@NotNull PsiWhileStatement scope, PsiType type, String containerName) {
      final Project project = scope.getProject();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      @NonNls String baseName;
      if (containerName != null) {
        baseName = StringUtils.createSingularFromName(containerName);
      }
      else {
        final SuggestedNameInfo suggestions = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type);
        final String[] names = suggestions.names;
        if (names != null && names.length > 0) {
          baseName = names[0];
        }
        else {
          baseName = "value";
        }
      }
      if (baseName == null || baseName.isEmpty()) {
        baseName = "value";
      }
      return codeStyleManager.suggestUniqueVariableName(baseName, scope, true);
    }

    @Nullable
    private static PsiStatement getFirstStatement(@NotNull PsiStatement body) {
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement block = (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = block.getCodeBlock();
        return ArrayUtil.getFirstElement(codeBlock.getStatements());
      }
      else {
        return body;
      }
    }
  }
}