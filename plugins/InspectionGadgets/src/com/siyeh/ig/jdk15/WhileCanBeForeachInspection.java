/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.jdk15;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.StringUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class WhileCanBeForeachInspection extends StatementInspection {

    public String getID() {
        return "WhileLoopReplaceableByForEach";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new WhileCanBeForeachVisitor();
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "while.can.be.foreach.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new WhileCanBeForeachFix();
    }

    private static class WhileCanBeForeachFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message("foreach.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement whileElement = descriptor.getPsiElement();
            final PsiWhileStatement whileStatement =
                    (PsiWhileStatement)whileElement.getParent();
            replaceWhileWithForEach(whileStatement);
        }

        @Nullable
        private static void replaceWhileWithForEach(
                @NotNull PsiWhileStatement whileStatement)
                throws IncorrectOperationException {
            final PsiStatement body = whileStatement.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement firstStatement = getFirstStatement(body);
            final PsiStatement initialization =
                    getPreviousStatement(whileStatement);
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement)initialization;
            if (declaration == null) {
                return;
            }
            final PsiElement declaredElement =
                    declaration.getDeclaredElements()[0];
            if(!(declaredElement instanceof PsiLocalVariable)){
                return;
            }
            final PsiLocalVariable iterator = (PsiLocalVariable)declaredElement;
            final PsiMethodCallExpression initializer =
                    (PsiMethodCallExpression)iterator.getInitializer();
            if (initializer == null) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    initializer.getMethodExpression();
            final PsiExpression collection =
                    methodExpression.getQualifierExpression();
            if (collection == null) {
                return;
            }
            final PsiClassType type = (PsiClassType)collection.getType();
            if (type == null) {
                return;
            }
            final PsiType[] parameters = type.getParameters();
            final String contentTypeString;
            if (parameters.length == 1) {
                final PsiType parameterType = parameters[0];
                if (parameterType instanceof PsiWildcardType) {
                    final PsiWildcardType wildcardType =
                            (PsiWildcardType)parameterType;
                    final PsiType bound = wildcardType.getBound();
                    if (bound == null) {
                        contentTypeString = "java.lang.Object";
                    } else {
                        contentTypeString = bound.getCanonicalText();
                    }
                } else if (parameterType != null) {
                    contentTypeString = parameterType.getCanonicalText();
                } else {
                    contentTypeString = "java.lang.Object";
                }
            } else {
                contentTypeString = "java.lang.Object";
            }
            final Project project = whileStatement.getProject();
            final PsiManager psiManager = PsiManager.getInstance(project);
            final PsiElementFactory elementFactory =
                    psiManager.getElementFactory();
            final PsiType contentType =
                    elementFactory.createTypeFromText(contentTypeString,
                            whileStatement);
            final String iteratorName = iterator.getName();
            final boolean isDeclaration =
                    isIteratorNextDeclaration(firstStatement, iteratorName,
                            contentTypeString);
            final PsiStatement statementToSkip;
            final String contentVariableName;
            if (isDeclaration) {
                final PsiDeclarationStatement declarationStatement =
                        (PsiDeclarationStatement)firstStatement;
                if (declarationStatement == null) {
                    return;
                }
                final PsiElement[] declaredElements =
                        declarationStatement.getDeclaredElements();
                final PsiLocalVariable localVar =
                        (PsiLocalVariable)declaredElements[0];
                contentVariableName = localVar.getName();
                statementToSkip = declarationStatement;
            } else {
                if (collection instanceof PsiReferenceExpression) {
                    final PsiJavaCodeReferenceElement referenceElement
                            = (PsiJavaCodeReferenceElement)collection;
                    final String collectionName =
                            referenceElement.getReferenceName();
                    contentVariableName = createNewVarName(
                            whileStatement,
                            contentType,
                            collectionName);
                } else {
                    contentVariableName =
                            createNewVarName(whileStatement, contentType, null);
                }

                statementToSkip = null;
            }
            final CodeStyleSettings codeStyleSettings =
                    CodeStyleSettingsManager.getSettings(project);
            @NonNls final String finalString;
            if(codeStyleSettings.GENERATE_FINAL_PARAMETERS) {
                finalString = "final ";
            } else {
                finalString = "";
            }
            @NonNls final StringBuilder out = new StringBuilder(64);
            out.append("for(");
            out.append(finalString);
            out.append(contentTypeString);
            out.append(' ');
            out.append(contentVariableName);
            out.append(": ");
            out.append(collection.getText());
            out.append(')');
            replaceIteratorNext(body, contentVariableName, iteratorName,
                    statementToSkip, out, contentTypeString);
            final Query<PsiReference> query =
                    ReferencesSearch.search(iterator, iterator.getUseScope());
            final Collection<PsiReference> usages = query.findAll();
            for (PsiReference usage : usages) {
                final PsiElement element = usage.getElement();
                if (!PsiTreeUtil.isAncestor(whileStatement, element, true)) {
                    final PsiAssignmentExpression assignment =
                            PsiTreeUtil.getParentOfType(element,
                                    PsiAssignmentExpression.class);
                    if (assignment == null) {
                        return;
                    }
                    final PsiExpression expression =
                            assignment.getRExpression();
                    initializer.delete();
                    iterator.setInitializer(expression);
                    final PsiElement statement = assignment.getParent();
                    final PsiElement lastChild = statement.getLastChild();
                    if (lastChild instanceof PsiComment) {
                        iterator.add(lastChild);
                    }
                    statement.replace(iterator);
                    iterator.delete();
                    break;
                }
            }
            final String result = out.toString();
            replaceStatementAndShortenClassNames(whileStatement, result);
        }

        private static void replaceIteratorNext(
                @NotNull PsiElement element, String contentVariableName,
                String iteratorName, PsiElement childToSkip,
                StringBuilder out, String contentTypeString) {
            if (isIteratorNext(element, iteratorName, contentTypeString)) {
                out.append(contentVariableName);
            } else {
                final PsiElement[] children = element.getChildren();
                if (children.length == 0) {
                    out.append(element.getText());
                } else {
                    boolean skippingWhiteSpace = false;
                    for (final PsiElement child : children) {
                        if (child.equals(childToSkip)) {
                            skippingWhiteSpace = true;
                        } else if (child instanceof PsiWhiteSpace &&
                                   skippingWhiteSpace) {
                            //don't do anything
                        } else {
                            skippingWhiteSpace = false;
                            replaceIteratorNext(child, contentVariableName,
                                    iteratorName,
                                    childToSkip, out, contentTypeString);
                        }
                    }
                }
            }
        }

        private static boolean isIteratorNextDeclaration(
                PsiStatement statement, String iteratorName,
                String contentType) {
            if (!(statement instanceof PsiDeclarationStatement)) {
                return false;
            }
            final PsiDeclarationStatement decl =
                    (PsiDeclarationStatement)statement;
            final PsiElement[] elements = decl.getDeclaredElements();
            if (elements.length != 1) {
                return false;
            }
            if (!(elements[0] instanceof PsiLocalVariable)) {
                return false;
            }
            final PsiLocalVariable var = (PsiLocalVariable)elements[0];
            final PsiExpression initializer = var.getInitializer();
            return isIteratorNext(initializer, iteratorName, contentType);
        }

        private static boolean isIteratorNext(
                PsiElement element, String iteratorName, String contentType){
            if (element == null) {
                return false;
            }
            if (element instanceof PsiTypeCastExpression) {
                final PsiTypeCastExpression castExpression =
                        (PsiTypeCastExpression)element;
                final PsiType type = castExpression.getType();
                if (type == null) {
                    return false;
                }
                final String presentableText = type.getPresentableText();
                if(!presentableText.equals(contentType)) {
                    return false;
                }
                final PsiExpression operand =
                        castExpression.getOperand();
                return isIteratorNext(operand, iteratorName, contentType);
            }
            if (!(element instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression)element;
            final PsiExpressionList argumentList =
                    callExpression.getArgumentList();
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 0) {
                return false;
            }
            final PsiReferenceExpression reference =
                    callExpression.getMethodExpression();
            final PsiExpression qualifier = reference.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            if (!iteratorName.equals(qualifier.getText())) {
                return false;
            }
            @NonNls final String referenceName = reference.getReferenceName();
            return "next".equals(referenceName);
        }

        private static String createNewVarName(
                @NotNull PsiWhileStatement scope, PsiType type,
                String containerName) {
            final Project project = scope.getProject();
            final CodeStyleManager codeStyleManager =
                    CodeStyleManager.getInstance(project);
            @NonNls String baseName;
            if (containerName != null) {
                baseName = StringUtils.createSingularFromName(containerName);
            } else {
                final SuggestedNameInfo suggestions =
                        codeStyleManager.suggestVariableName(
                                VariableKind.LOCAL_VARIABLE, null, null, type);
                final String[] names = suggestions.names;
                if (names != null && names.length > 0) {
                    baseName = names[0];
                } else {
                    baseName = "value";
                }
            }
            if (baseName == null || baseName.length() == 0) {
                baseName = "value";
            }
            return codeStyleManager.suggestUniqueVariableName(baseName, scope,
                    true);
        }

        @Nullable private static PsiStatement getFirstStatement(
                @NotNull PsiStatement body) {
            if (body instanceof PsiBlockStatement) {
                final PsiBlockStatement block = (PsiBlockStatement)body;
                final PsiCodeBlock codeBlock = block.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length > 0) {
                    return statements[0];
                } else {
                    return null;
                }
            } else {
                return body;
            }
        }
    }

    private static class WhileCanBeForeachVisitor
            extends StatementInspectionVisitor {

        public void visitWhileStatement(
                @NotNull PsiWhileStatement whileStatement) {
            super.visitWhileStatement(whileStatement);
            final LanguageLevel languageLevel =
                    PsiUtil.getLanguageLevel(whileStatement);
            if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
                return;
            }
            if (!isCollectionLoopStatement(whileStatement)) {
                return;
            }
            registerStatementError(whileStatement);
        }

        private static boolean isCollectionLoopStatement(
                PsiWhileStatement whileStatement) {
            final PsiStatement initialization =
                    getPreviousStatement(whileStatement);
            if (initialization == null) {
                return false;
            }
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return false;
            }
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement)initialization;
            final PsiElement[] declaredElements =
                    declaration.getDeclaredElements();
            if (declaredElements.length != 1) {
                return false;
            }
            final PsiElement declaredElement = declaredElements[0];
            if (!(declaredElement instanceof PsiLocalVariable)) {
                return false;
            }
            final PsiLocalVariable declaredVariable =
                    (PsiLocalVariable) declaredElement;
            final PsiType declaredVariableType = declaredVariable.getType();
            if (!(declaredVariableType instanceof PsiClassType)) {
                return false;
            }
            final PsiClassType classType = (PsiClassType)declaredVariableType;
            final PsiClass declaredClass = classType.resolve();
            if (declaredClass == null) {
                return false;
            }
            if (!ClassUtils.isSubclass(declaredClass, "java.util.Iterator")) {
                return false;
            }
            final PsiExpression initialValue = declaredVariable.getInitializer();
            if (initialValue == null) {
                return false;
            }
            if (!(initialValue instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression initialCall =
                    (PsiMethodCallExpression)initialValue;
            final PsiReferenceExpression initialMethodExpression =
                    initialCall.getMethodExpression();
            @NonNls final String initialCallName =
                    initialMethodExpression.getReferenceName();
            if (!"iterator".equals(initialCallName)) {
                return false;
            }
            final PsiExpression qualifier =
                    initialMethodExpression.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            final PsiType qualifierType = qualifier.getType();
            if (!(qualifierType instanceof PsiClassType)) {
                return false;
            }
            final PsiClass qualifierClass =
                    ((PsiClassType)qualifierType).resolve();
            if (qualifierClass == null) {
                return false;
            }
            if (!ClassUtils.isSubclass(qualifierClass, "java.lang.Iterable") &&
                !ClassUtils.isSubclass(qualifierClass,
                        "java.util.Collection")) {
                return false;
            }
            final String iteratorName = declaredVariable.getName();
            final PsiExpression condition = whileStatement.getCondition();
            if (!isHasNext(condition, iteratorName)) {
                return false;
            }
            final PsiStatement body = whileStatement.getBody();
            if (body == null) {
                return false;
            }
            if (calculateCallsToIteratorNext(iteratorName, body) != 1) {
                return false;
            }
            if (isIteratorRemoveCalled(iteratorName, body)) {
                return false;
            }
            if (isIteratorHasNextCalled(iteratorName, body)) {
                return false;
            }
            if (isIteratorAssigned(iteratorName, body)) {
                return false;
            }
            final Query<PsiReference> query =
                    ReferencesSearch.search(declaredVariable,
                            declaredVariable.getUseScope());
            final Collection<PsiReference> usages = query.findAll();
            for (PsiReference usage : usages) {
                final PsiElement element = usage.getElement();
                if (!PsiTreeUtil.isAncestor(whileStatement, element, true)) {
                    if (!(element instanceof PsiReferenceExpression)) {
                        return false;
                    }
                    final PsiReferenceExpression referenceExpression =
                            (PsiReferenceExpression)element;
                    if (!PsiUtil.isOnAssignmentLeftHand(referenceExpression)) {
                        return false;
                    }
                    break;
                }
            }
            return true;
        }

        private static boolean isHasNext(PsiExpression condition,
                                         String iterator) {
            if (!(condition instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression)condition;
            final PsiExpressionList argumentList = call.getArgumentList();
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 0) {
                return false;
            }
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.HAS_NEXT.equals(methodName)) {
                return false;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return true;
            }
            final String target = qualifier.getText();
            return iterator.equals(target);
        }

        private static int calculateCallsToIteratorNext(String iteratorName,
                                                        PsiStatement body) {
            final NumCallsToIteratorNextVisitor visitor =
                    new NumCallsToIteratorNextVisitor(iteratorName);
            body.accept(visitor);
            return visitor.getNumCallsToIteratorNext();
        }

        private static boolean isIteratorRemoveCalled(String iteratorName,
                                                      PsiStatement body) {
            final IteratorRemoveVisitor visitor =
                    new IteratorRemoveVisitor(iteratorName);
            body.accept(visitor);
            return visitor.isRemoveCalled();
        }

        private static boolean isIteratorHasNextCalled(String iteratorName,
                                                       PsiStatement body) {
            final IteratorHasNextVisitor visitor =
                    new IteratorHasNextVisitor(iteratorName);
            body.accept(visitor);
            return visitor.isHasNextCalled();
        }

        private static boolean isIteratorAssigned(String iteratorName,
                                                  PsiStatement body) {
            final IteratorAssignmentVisitor visitor =
                    new IteratorAssignmentVisitor(iteratorName);
            body.accept(visitor);
            return visitor.isIteratorAssigned();
        }
    }

    @Nullable
    public static PsiStatement getPreviousStatement(
            PsiWhileStatement statement) {
        final PsiElement prevStatement =
                PsiTreeUtil.skipSiblingsBackward(statement,
                    PsiWhiteSpace.class, PsiComment.class);
        if (prevStatement == null || !(prevStatement instanceof PsiStatement)) {
            return null;
        }
        return (PsiStatement)prevStatement;
    }

    private static class NumCallsToIteratorNextVisitor
            extends PsiRecursiveElementVisitor {

        private int numCallsToIteratorNext = 0;
        private final String iteratorName;

        private NumCallsToIteratorNextVisitor(String iteratorName) {
            super();
            this.iteratorName = iteratorName;
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression callExpression) {
            super.visitMethodCallExpression(callExpression);
            final PsiReferenceExpression methodExpression =
                    callExpression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!"next".equals(methodName)) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final String qualifierText = qualifier.getText();
            if (!iteratorName.equals(qualifierText)) {
                return;
            }
            numCallsToIteratorNext++;
        }

        public int getNumCallsToIteratorNext() {
            return numCallsToIteratorNext;
        }
    }

    private static class IteratorAssignmentVisitor
            extends PsiRecursiveElementVisitor {

        private boolean iteratorAssigned = false;
        private final String iteratorName;

        private IteratorAssignmentVisitor(@NotNull String iteratorName) {
            super();
            this.iteratorName = iteratorName;
        }

        public void visitElement(@NotNull PsiElement element) {
            if (!iteratorAssigned) {
                super.visitElement(element);
            }
        }

        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression exp) {
            super.visitAssignmentExpression(exp);
            final PsiExpression lhs = exp.getLExpression();
            final String lhsText = lhs.getText();
            if (iteratorName.equals(lhsText)) {
                iteratorAssigned = true;
            }
        }

        public boolean isIteratorAssigned() {
            return iteratorAssigned;
        }
    }

    private static class IteratorRemoveVisitor
            extends PsiRecursiveElementVisitor {

        private boolean removeCalled = false;
        private final String iteratorName;

        private IteratorRemoveVisitor(@NotNull String iteratorName) {
            super();
            this.iteratorName = iteratorName;
        }

        public void visitElement(@NotNull PsiElement element) {
            if (!removeCalled) {
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String name = methodExpression.getReferenceName();
            if (!"remove".equals(name)) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier != null) {
                final String qualifierText = qualifier.getText();
                if (iteratorName.equals(qualifierText)) {
                    removeCalled = true;
                }
            }
        }

        public boolean isRemoveCalled() {
            return removeCalled;
        }
    }

    private static class IteratorHasNextVisitor
            extends PsiRecursiveElementVisitor {

        private boolean hasNextCalled = false;
        private final String iteratorName;

        private IteratorHasNextVisitor(String iteratorName) {
            super();
            this.iteratorName = iteratorName;
        }

        public void visitElement(@NotNull PsiElement element) {
            if (!hasNextCalled) {
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String name = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.HAS_NEXT.equals(name)) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier != null) {
                final String qualifierText = qualifier.getText();
                if (iteratorName.equals(qualifierText)) {
                    hasNextCalled = true;
                }
            }
        }

        public boolean isHasNextCalled() {
            return hasNextCalled;
        }
    }
}