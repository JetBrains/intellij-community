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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.StringUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ForCanBeForeachInspection extends StatementInspection{

    public String getID(){
        return "ForLoopReplaceableByForEach";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "for.can.be.foreach.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "for.can.be.foreach.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ForCanBeForeachVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new ForCanBeForeachFix();
    }

    private static class ForCanBeForeachFix extends InspectionGadgetsFix{

        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message("foreach.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement forElement = descriptor.getPsiElement();
            if(!(forElement.getParent() instanceof PsiForStatement)){
                return;
            }
            final PsiForStatement forStatement =
                    (PsiForStatement) forElement.getParent();
            final String newExpression;
            if(isArrayLoopStatement(forStatement)){
                newExpression = createArrayIterationText(forStatement);
            } else if(isCollectionLoopStatement(forStatement)){
                newExpression = createCollectionIterationText(forStatement);
            } else if(isIndexedListLoopStatement(forStatement)){
                newExpression = createListIterationText(forStatement);
            } else{
                return;
            }
            if (newExpression == null) {
                return;
            }
            replaceStatementAndShortenClassNames(forStatement, newExpression);
        }

        @Nullable
        private static String createListIterationText(
                @NotNull PsiForStatement forStatement) {
            final String text = forStatement.getText();
            final int length = text.length();
            @NonNls final StringBuffer out = new StringBuffer(length);
            final PsiBinaryExpression condition =
                    (PsiBinaryExpression)forStatement.getCondition();
            if (condition == null) {
                return null;
            }
            final PsiExpression lhs = condition.getLOperand();
            final String indexName = lhs.getText();
            final PsiExpression rOperand = condition.getROperand();
            if (rOperand == null) {
                return null;
            }
            final PsiMethodCallExpression methodCallExpression =
                    ((PsiMethodCallExpression)
                            ParenthesesUtils.stripParentheses(rOperand));
            if (methodCallExpression == null) {
                return null;
            }
            final PsiReferenceExpression listLengthExpression =
                    methodCallExpression.getMethodExpression();
            final PsiReferenceExpression listReference =
                    (PsiReferenceExpression)
                            listLengthExpression.getQualifierExpression();
            if (listReference == null) {
                return null;
            }
            final PsiClassType listType = (PsiClassType)listReference.getType();
            if (listType == null) {
                return null;
            }
            final PsiClassType.ClassResolveResult resolveResult =
                    listType.resolveGenerics();
            final PsiClass listClass = resolveResult.getElement();
            PsiType componentType = null;
            if (listClass != null) {
                final Project project = forStatement.getProject();
                final PsiManager manager = forStatement.getManager();
                final PsiClass iterableClass =
                        manager.findClass("java.lang.Iterable",
                                GlobalSearchScope.allScope(project));
                if (iterableClass != null &&
                        iterableClass.getTypeParameters().length == 1) {
                    final PsiSubstitutor substitutor =
                            TypeConversionUtil.getClassSubstitutor(
                                    iterableClass, listClass,
                                    resolveResult.getSubstitutor());
                    if (substitutor != null) {
                        componentType = substitutor.substitute(
                                iterableClass.getTypeParameters()[0]);
                        if (componentType instanceof PsiWildcardType) {
                            final PsiWildcardType type =
                                    (PsiWildcardType)componentType;
                            componentType = type.getExtendsBound();
                        }
                    }
                }
            }
            final String type;
            if (componentType == null) {
                type = "java.lang.Object";
            } else {
                type = componentType.getCanonicalText();
            }
            final String listName = listReference.getText();
            final PsiStatement body = forStatement.getBody();
            final PsiStatement firstStatement = getFirstStatement(body);
            final boolean isDeclaration = isListElementDeclaration(
                    firstStatement, listName, indexName, componentType);
            final String contentVariableName;
            @NonNls final String finalString;
            final PsiStatement statementToSkip;
            if (isDeclaration) {
                final PsiDeclarationStatement declarationStatement =
                        (PsiDeclarationStatement)firstStatement;
                assert declarationStatement != null;
                final PsiElement[] declaredElements =
                        declarationStatement.getDeclaredElements();
                final PsiLocalVariable localVar =
                        (PsiLocalVariable)declaredElements[0];
                contentVariableName = localVar.getName();
                statementToSkip = declarationStatement;
                if (localVar.hasModifierProperty(PsiModifier.FINAL)) {
                    finalString = "final ";
                } else {
                    finalString = "";
                }
            } else {
                final String collectionName =
                        listReference.getReferenceName();
                contentVariableName = createNewVarName(forStatement,
                        componentType, collectionName);
                finalString = "";
                statementToSkip = null;
            }
            out.append("for(");
            out.append(finalString);
            out.append(type);
            out.append(' ');
            out.append(contentVariableName);
            out.append(": ");
            out.append(listName);
            out.append(')');
            if (body != null) {
                replaceCollectionGetAccess(body, contentVariableName, listName,
                        indexName, statementToSkip, out);
            }
            return out.toString();
        }

        @Nullable
        private static String createCollectionIterationText(
                @NotNull PsiForStatement forStatement)
                throws IncorrectOperationException{
            final String text = forStatement.getText();
            final int length = text.length();
            @NonNls final StringBuffer out = new StringBuffer(length);
            final PsiStatement body = forStatement.getBody();
            final PsiStatement firstStatement = getFirstStatement(body);
            final PsiStatement initialization =
                    forStatement.getInitialization();
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement) initialization;
            if (declaration == null) {
                return null;
            }
            final PsiLocalVariable iterator =
                    (PsiLocalVariable) declaration.getDeclaredElements()[0];
            final PsiClassType iteratorType = (PsiClassType) iterator.getType();
            final PsiType[] iteratorTypeParameters =
                    iteratorType.getParameters();
            PsiType iteratedContentsType = null;
            if(iteratorTypeParameters.length == 1){
                final PsiType parameterType = iteratorTypeParameters[0];
                if(parameterType instanceof PsiWildcardType){
                    final PsiWildcardType wildcardType =
                            (PsiWildcardType) parameterType;
                    iteratedContentsType = wildcardType.getExtendsBound();
                } else{
                    iteratedContentsType = parameterType;
                }
            }
            final PsiMethodCallExpression initializer =
                    (PsiMethodCallExpression) iterator.getInitializer();
            if (initializer == null) {
                return null;
            }
            final PsiReferenceExpression methodExpression =
                    initializer.getMethodExpression();
            final PsiExpression collection =
                    methodExpression.getQualifierExpression();
            if (collection == null) {
                return null;
            }
            final PsiClassType collectionType =
                    (PsiClassType) collection.getType();
            if (collectionType == null) {
                return null;
            }
            final PsiType[] parameters = collectionType.getParameters();
            final String collectionContentsTypeString;
            if(parameters.length == 1){
                final PsiType parameterType = parameters[0];
                if(parameterType instanceof PsiWildcardType){
                    final PsiWildcardType wildcardType =
                            (PsiWildcardType) parameterType;
                    final PsiType bound = wildcardType.getExtendsBound();
                    collectionContentsTypeString = bound.getCanonicalText();
                } else if(parameterType != null){
                    collectionContentsTypeString =
                            parameterType.getCanonicalText();
                } else{
                    collectionContentsTypeString = "java.lang.Object";
                }
            } else{
                collectionContentsTypeString = "java.lang.Object";
            }
            final String contentTypeString;
            if(iteratedContentsType != null){
                contentTypeString = iteratedContentsType.getCanonicalText();
            } else{
                contentTypeString = collectionContentsTypeString;
            }
            final PsiManager psiManager = forStatement.getManager();
            final PsiElementFactory elementFactory =
                    psiManager.getElementFactory();
            final PsiType contentType =
                    elementFactory.createTypeFromText(contentTypeString,
                            forStatement);
            final String iteratorName = iterator.getName();
            final boolean isDeclaration =
                    isIteratorNextDeclaration(firstStatement, iteratorName,
                            contentTypeString);
            final PsiStatement statementToSkip;
            @NonNls final String finalString;
            final String contentVariableName;
            if(isDeclaration){
                final PsiDeclarationStatement declarationStatement =
                        (PsiDeclarationStatement) firstStatement;
                assert declarationStatement != null;
                final PsiElement[] declaredElements =
                        declarationStatement.getDeclaredElements();
                final PsiLocalVariable localVar =
                        (PsiLocalVariable) declaredElements[0];
                contentVariableName = localVar.getName();
                statementToSkip = declarationStatement;
                if(localVar.hasModifierProperty(PsiModifier.FINAL)){
                    finalString = "final ";
                } else{
                    finalString = "";
                }
            } else{
                if(collection instanceof PsiReferenceExpression){
                    final PsiReferenceExpression referenceExpression =
                            (PsiReferenceExpression)collection;
                    final String collectionName =
                            referenceExpression.getReferenceName();
                    contentVariableName = createNewVarName(forStatement,
                            contentType, collectionName);
                } else{
                    contentVariableName = createNewVarName(forStatement,
                            contentType, null);
                }
                final Project project = forStatement.getProject();
                final CodeStyleSettings codeStyleSettings =
                        CodeStyleSettingsManager.getSettings(project);
                if(codeStyleSettings.GENERATE_FINAL_LOCALS){
                    finalString = "final ";
                } else{
                    finalString = "";
                }
                statementToSkip = null;
            }
            @NonNls final String iterableTypeString = "java.lang.Iterable<"
                    + contentTypeString + '>';
            final PsiManager manager = forStatement.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiType collectionContentsType =
                    factory.createTypeFromText(collectionContentsTypeString,
                            forStatement);
            final String castString;
            if(iteratedContentsType == null ||
                    iteratedContentsType.isAssignableFrom(
                            collectionContentsType)){
                castString = "";
            } else{
                castString = '(' + iterableTypeString + ')';
            }
            out.append("for(");
            out.append(finalString);
            out.append(contentTypeString);
            out.append(' ');
            out.append(contentVariableName);
            out.append(": ");
            out.append(castString);
            out.append(collection.getText());
            out.append(')');
            replaceIteratorNext(body, contentVariableName, iteratorName,
                    statementToSkip, out, contentTypeString);
            return out.toString();
        }

        @Nullable
        private static String createArrayIterationText(
                @NotNull PsiForStatement forStatement) {
            final String text = forStatement.getText();
            final int length = text.length();
            @NonNls final StringBuffer out = new StringBuffer(length);
            final PsiExpression condition = forStatement.getCondition();
            final PsiBinaryExpression strippedCondition =
                    (PsiBinaryExpression)ParenthesesUtils.stripParentheses(
                            condition);
            if (strippedCondition == null) {
                return null;
            }
            final PsiExpression lhs =
                    ParenthesesUtils.stripParentheses(
                            strippedCondition.getLOperand());
            if (lhs == null) {
                return null;
            }
            final String indexName = lhs.getText();
            final PsiExpression rhs = strippedCondition.getROperand();
            final PsiReferenceExpression arrayLengthExpression =
                    (PsiReferenceExpression)ParenthesesUtils.stripParentheses(
                            rhs);
            assert arrayLengthExpression != null;
            final PsiReferenceExpression arrayReference =
                    (PsiReferenceExpression)
                            arrayLengthExpression.getQualifierExpression();
            if (arrayReference == null) {
                return null;
            }
            final PsiArrayType arrayType =
                    (PsiArrayType) arrayReference.getType();
            if (arrayType == null) {
                return null;
            }
            final PsiType componentType = arrayType.getComponentType();
            final String type = componentType.getPresentableText();
            final String arrayName = arrayReference.getText();
            final PsiStatement body = forStatement.getBody();
            final PsiStatement firstStatement = getFirstStatement(body);
            final boolean isDeclaration =
                    isArrayElementDeclaration(firstStatement, arrayName,
                            indexName);
            final String contentVariableName;
            @NonNls final String finalString;
            final PsiStatement statementToSkip;
            if(isDeclaration){
                final PsiDeclarationStatement declarationStatement =
                        (PsiDeclarationStatement) firstStatement;
                assert declarationStatement != null;
                final PsiElement[] declaredElements =
                        declarationStatement.getDeclaredElements();
                final PsiLocalVariable localVar =
                        (PsiLocalVariable) declaredElements[0];
                contentVariableName = localVar.getName();
                statementToSkip = declarationStatement;
                if(localVar.hasModifierProperty(PsiModifier.FINAL)){
                    finalString = "final ";
                } else{
                    finalString = "";
                }
            } else{
                final String collectionName =
                        arrayReference.getReferenceName();
                contentVariableName = createNewVarName(forStatement,
                        componentType, collectionName);
                finalString = "";
                statementToSkip = null;
            }
            out.append("for(");
            out.append(finalString);
            out.append(type);
            out.append(' ');
            out.append(contentVariableName);
            out.append(": ");
            out.append(arrayName);
            out.append(')');
            if (body != null) {
                replaceArrayAccess(body, contentVariableName, arrayName,
                        indexName, statementToSkip, out);
            }
            return out.toString();
        }

        private static void replaceArrayAccess(
                PsiElement element, String contentVariableName,
                String arrayName, String indexName, PsiElement childToSkip,
                StringBuffer out){
            if(isArrayLookup(element, indexName, arrayName)){
                out.append(contentVariableName);
            } else{
                final PsiElement[] children = element.getChildren();
                if(children.length == 0){
                    final String text = element.getText();
                    if (PsiKeyword.INSTANCEOF.equals(text) &&
                            out.charAt(out.length() - 1) != ' ') {
                        out.append(' ');
                    }
                    out.append(text);
                } else{
                    boolean skippingWhiteSpace = false;
                    for(final PsiElement child : children){
                        if(child.equals(childToSkip)){
                            skippingWhiteSpace = true;
                        } else if(child instanceof PsiWhiteSpace &&
                                skippingWhiteSpace){
                            //don't do anything
                        } else{
                            skippingWhiteSpace = false;
                            replaceArrayAccess(child, contentVariableName,
                                    arrayName, indexName,
                                    childToSkip, out);
                        }
                    }
                }
            }
        }

        private static void replaceCollectionGetAccess(
                PsiElement element, String contentVariableName,
                String arrayName, String indexName,
                PsiElement childToSkip, StringBuffer out) {
            if (isListGetLookup(element, indexName, arrayName)){
                out.append(contentVariableName);
            } else{
                final PsiElement[] children = element.getChildren();
                if(children.length == 0){
                    final String text = element.getText();
                    if (PsiKeyword.INSTANCEOF.equals(text) &&
                            out.charAt(out.length() - 1) != ' ') {
                        out.append(' ');
                    }
                    out.append(text);
                } else{
                    boolean skippingWhiteSpace = false;
                    for(final PsiElement child : children){
                        if(child.equals(childToSkip)){
                            skippingWhiteSpace = true;
                        } else if(child instanceof PsiWhiteSpace &&
                                skippingWhiteSpace){
                            //don't do anything
                        } else{
                            skippingWhiteSpace = false;
                            replaceCollectionGetAccess(child,
                                    contentVariableName,
                                    arrayName, indexName,
                                    childToSkip, out);
                        }
                    }
                }
            }
        }

        private static boolean isListGetLookup(PsiElement element,
                                               String indexName,
                                               String arrayName){
            if(!(element instanceof PsiExpression &&
                    expressionIsListGetLookup((PsiExpression)element))){
                return false;
            }
            final PsiExpression expression = (PsiExpression) element;
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)
                            ParenthesesUtils.stripParentheses(expression);
            if (methodCallExpression == null) {
                return false;
            }
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if (qualifierExpression == null) {
                return false;
            }
            final String text = qualifierExpression.getText();
            if(!arrayName.equals(text)) {
                return false;
            }
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] expressions = argumentList.getExpressions();
            if(expressions.length != 1){
                return false;
            }
            return indexName.equals(expressions[0].getText());
        }

        private static void replaceIteratorNext(
                PsiElement element, String contentVariableName,
                String iteratorName, PsiElement childToSkip,
                StringBuffer out, String contentType) {
            if (isIteratorNext(element, iteratorName, contentType)){
                out.append(contentVariableName);
            } else{
                final PsiElement[] children = element.getChildren();
                if(children.length == 0){
                    final String text = element.getText();
                    if (PsiKeyword.INSTANCEOF.equals(text) &&
                            out.charAt(out.length() - 1) != ' ') {
                        out.append(' ');
                    }
                    out.append(text);
                } else{
                    boolean skippingWhiteSpace = false;
                    for(final PsiElement child : children){
                        if(child.equals(childToSkip)){
                            skippingWhiteSpace = true;
                        } else if(child instanceof PsiWhiteSpace &&
                                skippingWhiteSpace){
                            //don't do anything
                        } else{
                            skippingWhiteSpace = false;
                            replaceIteratorNext(child, contentVariableName,
                                    iteratorName, childToSkip, out, contentType);
                        }
                    }
                }
            }
        }

        private static boolean isArrayElementDeclaration(
                PsiStatement statement, String arrayName, String indexName){
            if(!(statement instanceof PsiDeclarationStatement)){
                return false;
            }
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement) statement;
            final PsiElement[] elements =
                    declarationStatement.getDeclaredElements();
            if(elements.length != 1){
                return false;
            }
            if(!(elements[0] instanceof PsiLocalVariable)){
                return false;
            }
            final PsiLocalVariable var = (PsiLocalVariable) elements[0];
            final PsiExpression initializer = var.getInitializer();
            return isArrayLookup(initializer, indexName, arrayName);
        }

        private static boolean isListElementDeclaration(
                PsiStatement statement, String arrayName,
                String indexName, PsiType type) {
            if (!(statement instanceof PsiDeclarationStatement)){
                return false;
            }
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement) statement;
            final PsiElement[] elements =
                    declarationStatement.getDeclaredElements();
            if(elements.length != 1){
                return false;
            }
            if(!(elements[0] instanceof PsiLocalVariable)){
                return false;
            }
            final PsiLocalVariable var = (PsiLocalVariable) elements[0];
            final PsiExpression initializer = var.getInitializer();
            if(!isListGetLookup(initializer, indexName,
                    arrayName)){
                return false;
            }
            return type != null && type.equals(var.getType());
        }

        private static boolean isIteratorNextDeclaration(
                PsiStatement statement, String iteratorName,
                String contentType){
            if(!(statement instanceof PsiDeclarationStatement)){
                return false;
            }
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement) statement;
            final PsiElement[] elements =
                    declarationStatement.getDeclaredElements();
            if(elements.length != 1){
                return false;
            }
            if(!(elements[0] instanceof PsiLocalVariable)){
                return false;
            }
            final PsiLocalVariable var = (PsiLocalVariable) elements[0];
            final PsiExpression initializer = var.getInitializer();
            return isIteratorNext(initializer, iteratorName, contentType);
        }

        private static boolean isArrayLookup(
                PsiElement element, String indexName, String arrayName){
            if(element == null){
                return false;
            }
            if(!(element instanceof PsiArrayAccessExpression)){
                return false;
            }
            final PsiArrayAccessExpression arrayAccess =
                    (PsiArrayAccessExpression) element;
            final PsiExpression indexExpression =
                    arrayAccess.getIndexExpression();
            if(indexExpression == null){
                return false;
            }
            if(!indexName.equals(indexExpression.getText())){
                return false;
            }
            final PsiExpression arrayExpression =
                    arrayAccess.getArrayExpression();
            return arrayName.equals(arrayExpression.getText());
        }

        private static boolean isIteratorNext(
                PsiElement element, String iteratorName, String contentType){
            if(element == null){
                return false;
            }
            if(element instanceof PsiTypeCastExpression){

                final PsiTypeCastExpression castExpression =
                        (PsiTypeCastExpression) element;
                final PsiType type = castExpression.getType();
                if(type == null){
                    return false;
                }
                final String presentableText = type.getPresentableText();
                if(!presentableText.equals(contentType)){
                    return false;
                }
                final PsiExpression operand =
                        castExpression.getOperand();
                return isIteratorNext(operand, iteratorName, contentType);
            }
            if(!(element instanceof PsiMethodCallExpression)){
                return false;
            }
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression) element;
            final PsiExpressionList argumentList =
                    callExpression.getArgumentList();
            final PsiExpression[] args = argumentList.getExpressions();
            if(args.length != 0){
                return false;
            }
            final PsiReferenceExpression reference =
                    callExpression.getMethodExpression();
            final PsiExpression qualifier = reference.getQualifierExpression();
            if(qualifier == null){
                return false;
            }
            if(!iteratorName.equals(qualifier.getText())){
                return false;
            }
            final String referenceName = reference.getReferenceName();
            return HardcodedMethodConstants.NEXT.equals(referenceName);
        }

        private static String createNewVarName(
                @NotNull PsiForStatement scope, PsiType type,
                String containerName) {
            final Project project = scope.getProject();
            final CodeStyleManager codeStyleManager =
                    CodeStyleManager.getInstance(project);
            @NonNls String baseName;
            if(containerName != null){
                baseName = StringUtils.createSingularFromName(containerName);
            } else{
                final SuggestedNameInfo suggestions =
                        codeStyleManager.suggestVariableName(
                                VariableKind.LOCAL_VARIABLE, null, null, type);
                final String[] names = suggestions.names;
                if(names != null && names.length > 0){
                    baseName = names[0];
                } else{
                    baseName = "value";
                }
            }
            if(baseName == null || baseName.length() == 0){
                baseName = "value";
            }
            return codeStyleManager.suggestUniqueVariableName(baseName, scope,
                    true);
        }

        @Nullable
        private static PsiStatement getFirstStatement(PsiStatement body){
            if (!(body instanceof PsiBlockStatement)) {
                return body;
            }
            final PsiBlockStatement block = (PsiBlockStatement) body;
            final PsiCodeBlock codeBlock = block.getCodeBlock();
            final PsiStatement[] statements = codeBlock.getStatements();
            if(statements.length <= 0){
                return null;
            }
            return statements[0];
        }
    }

    private static class ForCanBeForeachVisitor
            extends StatementInspectionVisitor{

        public void visitForStatement(@NotNull PsiForStatement forStatement){
            super.visitForStatement(forStatement);
            final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(forStatement);
            if(languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
                return;
            }
            if(isArrayLoopStatement(forStatement)
                    || isCollectionLoopStatement(forStatement)
                    || isIndexedListLoopStatement(forStatement)){
                registerStatementError(forStatement);
            }
        }
    }

    static boolean isIndexedListLoopStatement(
            PsiForStatement forStatement){
        final PsiStatement initialization = forStatement.getInitialization();
        if(!(initialization instanceof PsiDeclarationStatement)){
            return false;
        }
        final PsiDeclarationStatement declaration =
                (PsiDeclarationStatement) initialization;
        if(declaration.getDeclaredElements().length != 1){
            return false;
        }
        final PsiLocalVariable indexVar = (PsiLocalVariable) declaration
                .getDeclaredElements()[0];
        final PsiExpression initialValue = indexVar.getInitializer();
        if(initialValue == null){
            return false;
        }
        final String initializerText = initialValue.getText();
        if(!"0".equals(initializerText)){
            return false;
        }
        final PsiExpression condition = forStatement.getCondition();
        if(!isListSizeComparison(condition, indexVar)){
            return false;
        }
        final PsiStatement update = forStatement.getUpdate();
        if(!isIncrement(update, indexVar)){
            return false;
        }
        final PsiReferenceExpression collectionReference =
                getVariableReferenceFromCondition(condition);
        if (collectionReference == null) {
            return false;
        }
        final PsiElement resolved = collectionReference.resolve();
        if(!(resolved instanceof PsiVariable)){
            return false;
        }
        final PsiVariable variable = (PsiVariable)resolved;
        final PsiStatement body = forStatement.getBody();
        if (body == null) {
            return true;
        }
        if(!isIndexVarOnlyUsedAsListIndex(variable, indexVar, body)) {
            return false;
        }
        return !VariableAccessUtils.variableIsAssigned(variable, body);
    }

    static boolean isArrayLoopStatement(PsiForStatement forStatement){
        final PsiStatement initialization = forStatement.getInitialization();
        if(!(initialization instanceof PsiDeclarationStatement)){
            return false;
        }
        final PsiDeclarationStatement declaration =
                (PsiDeclarationStatement) initialization;
        if(declaration.getDeclaredElements().length != 1){
            return false;
        }
        final PsiLocalVariable indexVar =
                (PsiLocalVariable) declaration.getDeclaredElements()[0];
        final PsiExpression initialValue = indexVar.getInitializer();
        if(initialValue == null){
            return false;
        }
        final PsiManager manager = initialValue.getManager();
        final PsiConstantEvaluationHelper constantEvaluationHelper =
                manager.getConstantEvaluationHelper();
        final Object constant =
                constantEvaluationHelper.computeConstantExpression(initialValue);
        if (!(constant instanceof Integer)) {
            return false;
        }
        final Integer integer = (Integer)constant;
        if (integer.intValue() != 0) {
            return false;
        }
        final PsiExpression condition = forStatement.getCondition();
        if(!isArrayLengthComparison(condition, indexVar)){
            return false;
        }
        final PsiStatement update = forStatement.getUpdate();
        if(!isIncrement(update, indexVar)){
            return false;
        }
        final PsiReferenceExpression arrayReference =
                getVariableReferenceFromCondition(condition);
        if (arrayReference == null) {
            return false;
        }
        final String arrayName = arrayReference.getText();
        final PsiStatement body = forStatement.getBody();
        if (body == null) {
            return true;
        }
        if(!isIndexVarOnlyUsedAsIndex(arrayName, indexVar, body)){
            return false;
        }
        final PsiElement element = arrayReference.resolve();
        if (!(element instanceof PsiVariable)){
            return false;
        }
        final PsiVariable arrayVariable = (PsiVariable)element;
        if (VariableAccessUtils.variableIsAssigned(arrayVariable, body)) {
            return false;
        }
        return !VariableAccessUtils.arrayContentsAreAssigned(arrayVariable, body);
    }

    private static boolean isIndexVarOnlyUsedAsIndex(
            String arrayName, PsiLocalVariable indexVar, PsiStatement body){
        if (body == null) {
            return true;
        }
        final IndexOnlyUsedAsIndexVisitor visitor =
                new IndexOnlyUsedAsIndexVisitor(arrayName, indexVar);
        body.accept(visitor);
        return visitor.isIndexVariableUsedOnlyAsIndex();
    }

    private static boolean isIndexVarOnlyUsedAsListIndex(
            PsiVariable collection, PsiLocalVariable indexVar,
            PsiStatement body){
        if (body == null) {
            return true;
        }
        final VariableOnlyUsedAsListIndexVisitor visitor =
                new VariableOnlyUsedAsListIndexVisitor(collection, indexVar);
        body.accept(visitor);
        return visitor.isIndexVariableUsedOnlyAsIndex();
    }

    static boolean isCollectionLoopStatement(
            PsiForStatement forStatement){
        final PsiStatement initialization = forStatement.getInitialization();
        if(!(initialization instanceof PsiDeclarationStatement)){
            return false;
        }
        final PsiDeclarationStatement declaration =
                (PsiDeclarationStatement) initialization;
        if(declaration.getDeclaredElements().length != 1){
            return false;
        }
        final PsiLocalVariable variable =
                (PsiLocalVariable) declaration.getDeclaredElements()[0];
        if(variable == null){
            return false;
        }
        final PsiType declaredVarType = variable.getType();
        if(!(declaredVarType instanceof PsiClassType)){
            return false;
        }
        final PsiClassType classType = (PsiClassType) declaredVarType;
        final PsiClass declaredClass = classType.resolve();
        if(declaredClass == null){
            return false;
        }
        if(!ClassUtils.isSubclass(declaredClass, "java.util.Iterator")){
            return false;
        }
        final PsiExpression initialValue = variable.getInitializer();
        if(initialValue == null){
            return false;
        }
        if(!(initialValue instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression initialCall =
                (PsiMethodCallExpression) initialValue;
        final PsiReferenceExpression initialMethodExpression =
                initialCall.getMethodExpression();
        final String initialCallName =
                initialMethodExpression.getReferenceName();
        if(!HardcodedMethodConstants.ITERATOR.equals(initialCallName)){
            return false;
        }
        final PsiExpression qualifier =
                initialMethodExpression.getQualifierExpression();
        if(qualifier == null){
            return false;
        }
        final PsiType qualifierType = qualifier.getType();
        if(!(qualifierType instanceof PsiClassType)){
            return false;
        }
        final PsiClass qualifierClass =
                ((PsiClassType) qualifierType).resolve();
        if(qualifierClass == null){
            return false;
        }
        if(!ClassUtils.isSubclass(qualifierClass, "java.lang.Iterable") &&
                !ClassUtils.isSubclass(qualifierClass, "java.util.Collection")){
            return false;
        }
        final String iteratorName = variable.getName();
        final PsiExpression condition = forStatement.getCondition();
        if(!isHasNext(condition, iteratorName)){
            return false;
        }
        final PsiStatement update = forStatement.getUpdate();
        if(update != null && !(update instanceof PsiEmptyStatement)){
            return false;
        }
        final PsiStatement body = forStatement.getBody();
        if (body == null) {
            return false;
        }
        if(calculateCallsToIteratorNext(iteratorName, body) != 1){
            return false;
        }
        if(isIteratorRemoveCalled(iteratorName, body)){
            return false;
        }
        if(isIteratorHasNextCalled(iteratorName, body)){
            return false;
        }
        return !VariableAccessUtils.variableIsAssigned(variable, body);
    }

    private static int calculateCallsToIteratorNext(String iteratorName,
                                                    PsiStatement body){
        if (body == null) {
            return 0;
        }
        final NumCallsToIteratorNextVisitor visitor =
                new NumCallsToIteratorNextVisitor(iteratorName);
        body.accept(visitor);
        return visitor.getNumCallsToIteratorNext();
    }

    private static boolean isIteratorRemoveCalled(String iteratorName,
                                                  PsiStatement body){
        final IteratorRemoveVisitor visitor =
                new IteratorRemoveVisitor(iteratorName);
        body.accept(visitor);
        return visitor.isRemoveCalled();
    }

    private static boolean isIteratorHasNextCalled(String iteratorName,
                                                   PsiStatement body){
        final IteratorHasNextVisitor visitor =
                new IteratorHasNextVisitor(iteratorName);
        body.accept(visitor);
        return visitor.isHasNextCalled();
    }

    private static boolean isHasNext(PsiExpression condition, String iterator){
        if(!(condition instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) condition;
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        if(args.length != 0){
            return false;
        }
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if(!HardcodedMethodConstants.HAS_NEXT.equals(methodName)){
            return false;
        }
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if(qualifier == null){
            return true;
        }
        final String target = qualifier.getText();
        return iterator.equals(target);
    }

    @Nullable
    private static PsiReferenceExpression getVariableReferenceFromCondition(
            PsiExpression condition){
        final PsiExpression strippedCondition =
                ParenthesesUtils.stripParentheses(condition);
        final PsiBinaryExpression binaryExp =
                (PsiBinaryExpression) strippedCondition;
        if (binaryExp == null) {
            return null;
        }
        final PsiExpression rhs = binaryExp.getROperand();
        PsiExpression strippedRhs =
                ParenthesesUtils.stripParentheses(rhs);
        if(strippedRhs instanceof PsiMethodCallExpression){
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)strippedRhs;
            strippedRhs = methodCallExpression.getMethodExpression();
        }
        if (strippedRhs == null) {
            return null;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression)strippedRhs;
        return (PsiReferenceExpression)
                referenceExpression.getQualifierExpression();
    }

    private static boolean isIncrement(PsiStatement statement,
                                       PsiLocalVariable var){
        if(!(statement instanceof PsiExpressionStatement)){
            return false;
        }
        final PsiExpressionStatement expressionStatement =
                (PsiExpressionStatement)statement;
        PsiExpression exp =
                expressionStatement.getExpression();
        exp = ParenthesesUtils.stripParentheses(exp);
        if(exp instanceof PsiPrefixExpression){
            final PsiPrefixExpression prefixExp = (PsiPrefixExpression) exp;
            final PsiJavaToken sign = prefixExp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUSPLUS)){
                return false;
            }
            final PsiExpression operand = prefixExp.getOperand();
            return VariableAccessUtils.evaluatesToVariable(operand, var);
        } else if(exp instanceof PsiPostfixExpression){
            final PsiPostfixExpression postfixExp = (PsiPostfixExpression) exp;
            final PsiJavaToken sign = postfixExp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUSPLUS)){
                return false;
            }
            final PsiExpression operand = postfixExp.getOperand();
            return VariableAccessUtils.evaluatesToVariable(operand, var);
        }
        return false;
    }

    private static boolean isArrayLengthComparison(
            PsiExpression condition, PsiLocalVariable var) {
        final PsiExpression strippedCondition =
                ParenthesesUtils.stripParentheses(condition);
        if(!(strippedCondition instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExp =
                (PsiBinaryExpression) strippedCondition;
        final PsiJavaToken sign = binaryExp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.LT)){
            return false;
        }
        final PsiExpression lhs = binaryExp.getLOperand();
        if(!VariableAccessUtils.evaluatesToVariable(lhs, var)){
            return false;
        }
        final PsiExpression rhs = binaryExp.getROperand();
        if (rhs == null) {
            return false;
        }
        return expressionIsArrayLengthLookup(rhs);
    }

    private static boolean isListSizeComparison(PsiExpression condition,
                                                PsiLocalVariable var){
        final PsiExpression strippedCondition =
                ParenthesesUtils.stripParentheses(condition);
        if(!(strippedCondition instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExp =
                (PsiBinaryExpression) strippedCondition;
        final PsiJavaToken sign = binaryExp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.LT)){
            return false;
        }
        final PsiExpression lhs = binaryExp.getLOperand();
        if(!VariableAccessUtils.evaluatesToVariable(lhs, var)){
            return false;
        }
        final PsiExpression rhs = binaryExp.getROperand();
        return expressionIsListSizeLookup(rhs);
    }

    private static boolean expressionIsListSizeLookup(PsiExpression expression){
        return isListMethodCall(expression, HardcodedMethodConstants.SIZE);
    }

    static boolean expressionIsListGetLookup(PsiExpression expression){
        return isListMethodCall(expression, HardcodedMethodConstants.GET);
    }

    private static boolean isListMethodCall(PsiExpression expression,
                                            String methodName){
        final PsiExpression strippedExpression =
                ParenthesesUtils.stripParentheses(expression);
        if(!(strippedExpression instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression reference =
                (PsiMethodCallExpression) strippedExpression;
        final PsiReferenceExpression methodExpression =
                reference.getMethodExpression();
        final PsiExpression qualifierExpression =
                methodExpression.getQualifierExpression();
        if(!(qualifierExpression instanceof PsiReferenceExpression)){
            return false;
        }
        final PsiElement resolved = methodExpression.resolve();
        if(!(resolved instanceof PsiMethod)){
            return false;
        }
        final PsiMethod method = (PsiMethod) resolved;
        if(!methodName.equals(method.getName())){
            return false;
        }
        final PsiClass aClass = method.getContainingClass();
        return ClassUtils.isSubclass(aClass, "java.util.List");
    }

    private static boolean expressionIsArrayLengthLookup(
            @NotNull PsiExpression expression){
        final PsiExpression strippedExpression =
                ParenthesesUtils.stripParentheses(expression);
        if(!(strippedExpression instanceof PsiReferenceExpression)){
            return false;
        }
        final PsiReferenceExpression reference =
                (PsiReferenceExpression) strippedExpression;
        final String referenceName = reference.getReferenceName();
        if(!HardcodedMethodConstants.LENGTH.equals(referenceName)){
            return false;
        }
        final PsiExpression qualifier = reference.getQualifierExpression();
        if(!(qualifier instanceof PsiReferenceExpression)){
            return false;
        }
        final PsiType type = qualifier.getType();
        if(type == null){
            return false;
        }
        return type.getArrayDimensions() > 0;
    }

    private static class NumCallsToIteratorNextVisitor
            extends PsiRecursiveElementVisitor{

        private int numCallsToIteratorNext = 0;
        private final String iteratorName;

        NumCallsToIteratorNextVisitor(String iteratorName){
            this.iteratorName = iteratorName;
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression callExpression){
            super.visitMethodCallExpression(callExpression);
            final PsiReferenceExpression methodExpression =
                    callExpression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(!HardcodedMethodConstants.NEXT.equals(methodName)){
                return;
            }

            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier == null){
                return;
            }
            final String qualifierText = qualifier.getText();
            if(!iteratorName.equals(qualifierText)){
                return;
            }
            numCallsToIteratorNext++;
        }

        public int getNumCallsToIteratorNext(){
            return numCallsToIteratorNext;
        }
    }

    private static class IteratorRemoveVisitor
            extends PsiRecursiveElementVisitor{
        private boolean removeCalled = false;
        private final String iteratorName;

        IteratorRemoveVisitor(String iteratorName){
            this.iteratorName = iteratorName;
        }

        public void visitElement(@NotNull PsiElement element){
            if(!removeCalled){
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            if(removeCalled){
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String name = methodExpression.getReferenceName();
            if(!HardcodedMethodConstants.REMOVE.equals(name)){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier != null){
                final String qualifierText = qualifier.getText();
                if(iteratorName.equals(qualifierText)){
                    removeCalled = true;
                }
            }
        }

        public boolean isRemoveCalled(){
            return removeCalled;
        }
    }

    private static class IteratorHasNextVisitor
            extends PsiRecursiveElementVisitor{

        private boolean hasNextCalled = false;
        private final String iteratorName;

        IteratorHasNextVisitor(String iteratorName){
            this.iteratorName = iteratorName;
        }

        public void visitElement(@NotNull PsiElement element){
            if(!hasNextCalled){
                super.visitElement(element);
            }
        }

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            if(hasNextCalled){
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String name = methodExpression.getReferenceName();
            if(!HardcodedMethodConstants.HAS_NEXT.equals(name)){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier != null){
                final String qualifierText = qualifier.getText();
                if(iteratorName.equals(qualifierText)){
                    hasNextCalled = true;
                }
            }
        }

        public boolean isHasNextCalled(){
            return hasNextCalled;
        }
    }

    private static class IndexOnlyUsedAsIndexVisitor
            extends PsiRecursiveElementVisitor{

        private boolean indexVariableUsedOnlyAsIndex = true;
        private final String arrayName;
        private final PsiLocalVariable indexVariable;

        IndexOnlyUsedAsIndexVisitor(String arrayName,
                                    PsiLocalVariable indexVariable){
            this.arrayName = arrayName;
            this.indexVariable = indexVariable;
        }

        public void visitElement(@NotNull PsiElement element){
            if(indexVariableUsedOnlyAsIndex){
                super.visitElement(element);
            }
        }

        public void visitReferenceExpression(
                @NotNull PsiReferenceExpression reference){
            if(!indexVariableUsedOnlyAsIndex){
                return;
            }
            super.visitReferenceExpression(reference);

            final PsiElement element = reference.resolve();
            if(!indexVariable.equals(element)){
                return;
            }
            final PsiElement parent = reference.getParent();
            if(!(parent instanceof PsiArrayAccessExpression)){
                indexVariableUsedOnlyAsIndex = false;
                return;
            }
            final PsiArrayAccessExpression arrayAccess =
                    (PsiArrayAccessExpression) parent;
            final PsiExpression arrayExpression =
                    arrayAccess.getArrayExpression();
            final String text = arrayExpression.getText();
            if(!text.equals(arrayName)){
                indexVariableUsedOnlyAsIndex = false;
                return;
            }
            final PsiElement arrayExpressionContext = arrayAccess.getParent();
            if(arrayExpressionContext instanceof PsiAssignmentExpression){
                final PsiAssignmentExpression assignment =
                        (PsiAssignmentExpression) arrayExpressionContext;
                final PsiExpression lhs = assignment.getLExpression();
                if(lhs.equals(arrayAccess)){
                    indexVariableUsedOnlyAsIndex = false;
                }
            }
        }

        public boolean isIndexVariableUsedOnlyAsIndex(){
            return indexVariableUsedOnlyAsIndex;
        }
    }

    private static class VariableOnlyUsedAsListIndexVisitor
            extends PsiRecursiveElementVisitor{

        private boolean indexVariableUsedOnlyAsIndex = true;
        private final PsiLocalVariable indexVariable;
        private final PsiVariable collection;

        VariableOnlyUsedAsListIndexVisitor(
                @NotNull PsiVariable collection,
                @NotNull PsiLocalVariable indexVariable){
            this.collection = collection;
            this.indexVariable = indexVariable;
        }

        public void visitElement(@NotNull PsiElement element){
            if(indexVariableUsedOnlyAsIndex){
                super.visitElement(element);
            }
        }

        public void visitReferenceExpression(
                @NotNull PsiReferenceExpression reference){
            if(!indexVariableUsedOnlyAsIndex){
                return;
            }
            super.visitReferenceExpression(reference);
            final PsiElement element = reference.resolve();
            if(indexVariable.equals(element) &&
                    !isListIndexExpression(reference, collection)) {
                indexVariableUsedOnlyAsIndex = false;
            }
            if(collection.equals(element) &&
                    !isListReferenceInIndexExpression(reference, collection)){
                indexVariableUsedOnlyAsIndex = false;
            }
        }

        public boolean isIndexVariableUsedOnlyAsIndex(){
            return indexVariableUsedOnlyAsIndex;
        }

        private static boolean isListIndexExpression(
                PsiReferenceExpression reference, PsiVariable collection){
            final PsiElement referenceParent = reference.getParent();
            if(!(referenceParent instanceof PsiExpressionList)){
                return false;
            }
            final PsiExpressionList expressionList =
                    (PsiExpressionList) referenceParent;
            final PsiElement parent = expressionList.getParent();
            if(!(parent instanceof PsiMethodCallExpression)){
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) parent;
            return isListGetExpression(methodCallExpression, collection);
        }

        private static boolean isListReferenceInIndexExpression(
                PsiReferenceExpression ref, PsiVariable collection){
            final PsiElement parent = ref.getParent();
            if(!(parent instanceof PsiReferenceExpression)){
                return false;
            }
            if(!(parent.getParent() instanceof PsiMethodCallExpression)){
                return false;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) parent.getParent();
            return isListGetExpression(methodCallExpression, collection);
        }

        private static boolean isListGetExpression(
                PsiMethodCallExpression methodCallExpression,
                @NotNull PsiVariable collection){
            if(methodCallExpression == null){
                return false;
            }
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if(!(qualifierExpression instanceof PsiReferenceExpression)){
                return false;
            }
            final PsiReference reference = (PsiReference)qualifierExpression;
            if(!collection.equals(reference.resolve())){
                return false;
            }
            return expressionIsListGetLookup(methodCallExpression);
        }
    }
}