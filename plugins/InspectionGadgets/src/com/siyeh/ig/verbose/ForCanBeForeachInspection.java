package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;

public class ForCanBeForeachInspection extends StatementInspection {
    private final ForCanBeForeachFix fix = new ForCanBeForeachFix();

    public String getDisplayName() {
        return "'for' loop replacable by 'for each' (J2SDK 5.0 only)";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' loop replacable by 'for each'";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ForCanBeForeachVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private class ForCanBeForeachFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with 'for each'";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiElement forElement = descriptor.getPsiElement();
            final PsiForStatement forStatement = (PsiForStatement) forElement.getParent();
            if (isArrayLoopStatement(forStatement)) {
                final String newExpression = createArrayIterationText(forStatement, project);
                replaceStatement(project, forStatement, newExpression);
            } else if (isCollectionLoopStatement(forStatement)) {
                final String newExpression = createCollectionIterationText(forStatement, project);
                replaceStatement(project, forStatement, newExpression);
            }
        }

        private String createCollectionIterationText(PsiForStatement forStatement, Project project) {
            final int length = forStatement.getText().length();
            final StringBuffer out = new StringBuffer(length);
            final PsiStatement body = forStatement.getBody();
            final PsiStatement firstStatement = getFirstStatement(body);
            final String contentVariableName;
            final String finalString;
            final PsiStatement statementToSkip;
            final PsiStatement initialization = forStatement.getInitialization();
            final PsiDeclarationStatement declaration = (PsiDeclarationStatement) initialization;
            final PsiLocalVariable iterator = (PsiLocalVariable) declaration.getDeclaredElements()[0];

            final PsiMethodCallExpression initializer =
                    (PsiMethodCallExpression) iterator.getInitializer();
            final PsiExpression collection = initializer.getMethodExpression().getQualifierExpression();
            final String typeString = iterator.getTypeElement().getText();

            final String contentTypeString;
            if (typeString.indexOf((int) '<') >= 0) {
                final int contentTypeStart = typeString.indexOf((int) '<') + 1;
                final int contentTypeEnd = typeString.lastIndexOf((int) '>');
                contentTypeString = typeString.substring(contentTypeStart, contentTypeEnd);
            } else {
                contentTypeString = "Object";
            }
            final PsiManager psiManager = PsiManager.getInstance(project);
            final PsiElementFactory elementFactory = psiManager.getElementFactory();
            PsiType contentType;
            try {
                contentType = elementFactory.createTypeFromText(contentTypeString, forStatement);
            } catch (IncorrectOperationException e) {
                contentType = null;
            }

            final String iteratorName = iterator.getName();
            final boolean isDeclaration = isIteratorNextDeclaration(firstStatement, iteratorName);
            if (isDeclaration) {
                final PsiDeclarationStatement decl = (PsiDeclarationStatement) firstStatement;
                final PsiElement[] declaredElements = decl.getDeclaredElements();
                final PsiLocalVariable localVar = (PsiLocalVariable) declaredElements[0];
                contentVariableName = localVar.getName();
                statementToSkip = decl;
                if (localVar.hasModifierProperty(PsiModifier.FINAL)) {
                    finalString = "final ";
                } else {
                    finalString = "";
                }
            } else {
                contentVariableName = createNewVarName(project, forStatement, contentType);
                finalString = "";
                statementToSkip = null;
            }
            out.append("for(" + finalString + contentTypeString + ' ' + contentVariableName + ": " + collection.getText() + ')');
            replaceIteratorNext(body, contentVariableName, iteratorName, statementToSkip, out);
            return out.toString();
        }

        private String createArrayIterationText(PsiForStatement forStatement, Project project) {
            final int length = forStatement.getText().length();
            final StringBuffer out = new StringBuffer(length);
            final PsiBinaryExpression condition = (PsiBinaryExpression) forStatement.getCondition();
            final PsiExpression lhs = condition.getLOperand();
            final String indexName = lhs.getText();
            final PsiReferenceExpression arrayLengthExpression = (PsiReferenceExpression) condition.getROperand();
            final PsiReferenceExpression arrayReference = (PsiReferenceExpression) arrayLengthExpression.getQualifierExpression();
            final PsiArrayType arrayType = (PsiArrayType) arrayReference.getType();
            final PsiType componentType = arrayType.getComponentType();
            final String type = componentType.getPresentableText();
            final String arrayName = arrayReference.getText();
            final PsiStatement body = forStatement.getBody();
            final PsiStatement firstStatement = getFirstStatement(body);
            final String contentVariableName;
            final String finalString;
            final PsiStatement statementToSkip;
            final boolean isDeclaration = isArrayElementDeclaration(firstStatement, arrayName, indexName);
            if (isDeclaration) {
                final PsiDeclarationStatement decl = (PsiDeclarationStatement) firstStatement;
                final PsiElement[] declaredElements = decl.getDeclaredElements();
                final PsiLocalVariable localVar = (PsiLocalVariable) declaredElements[0];
                contentVariableName = localVar.getName();
                statementToSkip = decl;
                if (localVar.hasModifierProperty(PsiModifier.FINAL)) {
                    finalString = "final ";
                } else {
                    finalString = "";
                }
            } else {
                contentVariableName = createNewVarName(project, forStatement, componentType);
                finalString = "";
                statementToSkip = null;
            }
            out.append("for(" + finalString + type + ' ' + contentVariableName + ": " + arrayName + ')');
            replaceArrayAccess(body, contentVariableName, arrayName, indexName, statementToSkip, out);
            return out.toString();
        }

        private void replaceArrayAccess(PsiElement element, String contentVariableName, String arrayName, String indexName,
                                        PsiElement childToSkip, StringBuffer out) {
            if (isArrayLookup(element, indexName, arrayName)) {
                out.append(contentVariableName);
            } else {
                final PsiElement[] children = element.getChildren();
                if (children.length == 0) {
                    out.append(element.getText());
                } else {
                    boolean skippingWhiteSpace = false;
                    for (int i = 0; i < children.length; i++) {
                        final PsiElement child = children[i];

                        if (child.equals(childToSkip)) {
                            skippingWhiteSpace = true;
                        } else if (child instanceof PsiWhiteSpace && skippingWhiteSpace) {
                            //don't do anything
                        } else {
                            skippingWhiteSpace = false;
                            replaceArrayAccess(child, contentVariableName, arrayName, indexName, childToSkip, out);
                        }
                    }
                }
            }
        }

        private void replaceIteratorNext(PsiElement element, String contentVariableName, String iteratorName,
                                         PsiElement childToSkip, StringBuffer out) {

            if (isIteratorNext(element, iteratorName)) {
                out.append(contentVariableName);
            } else {
                final PsiElement[] children = element.getChildren();
                if (children.length == 0) {
                    out.append(element.getText());
                } else {
                    boolean skippingWhiteSpace = false;
                    for (int i = 0; i < children.length; i++) {
                        final PsiElement child = children[i];

                        if (child.equals(childToSkip)) {
                            skippingWhiteSpace = true;
                        } else if (child instanceof PsiWhiteSpace &&
                                skippingWhiteSpace) {
                            //don't do anything
                        } else {
                            skippingWhiteSpace = false;
                            replaceIteratorNext(child, contentVariableName,
                                    iteratorName,
                                    childToSkip, out);
                        }
                    }
                }
            }
        }

        private boolean isArrayElementDeclaration(PsiStatement statement, String arrayName, String indexName) {
            if (!(statement instanceof PsiDeclarationStatement)) {
                return false;
            }
            final PsiDeclarationStatement decl = (PsiDeclarationStatement) statement;
            final PsiElement[] elements = decl.getDeclaredElements();
            if (elements.length != 1) {
                return false;
            }
            if (!(elements[0] instanceof PsiLocalVariable)) {
                return false;
            }
            final PsiLocalVariable var = (PsiLocalVariable) elements[0];
            final PsiExpression initializer = var.getInitializer();
            return isArrayLookup(initializer, indexName, arrayName);
        }

        private boolean isIteratorNextDeclaration(PsiStatement statement, String iteratorName) {
            if (!(statement instanceof PsiDeclarationStatement)) {
                return false;
            }
            final PsiDeclarationStatement decl = (PsiDeclarationStatement) statement;
            final PsiElement[] elements = decl.getDeclaredElements();
            if (elements.length != 1) {
                return false;
            }
            if (!(elements[0] instanceof PsiLocalVariable)) {
                return false;
            }
            final PsiLocalVariable var = (PsiLocalVariable) elements[0];
            final PsiExpression initializer = var.getInitializer();
            return isIteratorNext(initializer, iteratorName);
        }

        private boolean isArrayLookup(PsiElement element, String indexName, String arrayName) {
            if (element == null) {
                return false;
            }
            if (!(element instanceof PsiArrayAccessExpression)) {
                return false;
            }
            final PsiArrayAccessExpression arrayAccess =
                    (PsiArrayAccessExpression) element;
            final PsiExpression indexExpression = arrayAccess.getIndexExpression();
            if (indexExpression == null) {
                return false;
            }
            if (!indexName.equals(indexExpression.getText())) {
                return false;
            }
            final PsiExpression arrayExpression = arrayAccess.getArrayExpression();
            if (arrayExpression == null) {
                return false;
            }
            if (!arrayName.equals(arrayExpression.getText())) {
                return false;
            }
            return true;
        }

        private boolean isIteratorNext(PsiElement element, String iteratorNameName) {
            if (element == null) {
                return false;
            }
            if (!(element instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression) element;
            final PsiExpressionList argumentList = callExpression.getArgumentList();
            if (argumentList == null) {
                return false;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if (args == null || args.length != 0) {
                return false;
            }
            final PsiReferenceExpression reference = callExpression.getMethodExpression();
            if (reference == null) {
                return false;
            }
            final PsiExpression qualifier = reference.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            if (!iteratorNameName.equals(qualifier.getText())) {
                return false;
            }
            final String referenceName = reference.getReferenceName();
            if (!"next".equals(referenceName)) {
                return false;
            }
            return true;
        }

        private String createNewVarName(Project project, PsiForStatement scope, PsiType type) {
            final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
            final SuggestedNameInfo suggestions =
                    codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type);
            final String[] names = suggestions.names;
            final String baseName;
            if (names != null && names.length > 0) {
                baseName = names[0];
            } else {
                baseName = "value";
            }
            return codeStyleManager.suggestUniqueVariableName(baseName, scope, true);
        }

        private PsiStatement getFirstStatement(PsiStatement body) {
            if (body instanceof PsiBlockStatement) {
                final PsiBlockStatement block = (PsiBlockStatement) body;
                return block.getCodeBlock().getStatements()[0];
            } else {
                return body;
            }
        }

    }

    private class ForCanBeForeachVisitor extends BaseInspectionVisitor {
        private ForCanBeForeachVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitForStatement(PsiForStatement forStatement) {
            super.visitForStatement(forStatement);
            final PsiManager manager = forStatement.getManager();
            final LanguageLevel languageLevel = manager.getEffectiveLanguageLevel();
            if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
                    languageLevel.equals(LanguageLevel.JDK_1_4)) {
                return;
            }
            if (!isArrayLoopStatement(forStatement) &&
                    !isCollectionLoopStatement(forStatement)) {
                return;
            }
            registerStatementError(forStatement);
        }

    }


    private boolean isArrayLoopStatement(PsiForStatement forStatement) {
        final PsiStatement initialization = forStatement.getInitialization();
        if (!(initialization instanceof PsiDeclarationStatement)) {
            return false;
        }
        final PsiDeclarationStatement declaration = (PsiDeclarationStatement) initialization;
        if (declaration.getDeclaredElements().length != 1) {
            return false;
        }
        final PsiLocalVariable indexVar = (PsiLocalVariable) declaration.getDeclaredElements()[0];
        final PsiExpression initialValue = indexVar.getInitializer();
        if (initialValue == null) {
            return false;
        }
        final String initializerText = initialValue.getText();
        if (!"0".equals(initializerText)) {
            return false;
        }
        final PsiExpression condition = forStatement.getCondition();
        if (!isArrayLengthComparison(condition, indexVar)) {
            return false;
        }
        final PsiStatement update = forStatement.getUpdate();
        if (!isIncrement(update, indexVar)) {
            return false;
        }
        final PsiReferenceExpression arrayReference = getArrayFromCondition(condition);
        final PsiStatement body = forStatement.getBody();
        if (body == null) {
            return false;
        }
        final String arrayName = arrayReference.getText();
        if (!indexVarOnlyUsedAsIndex(arrayName, indexVar, body)) {
            return false;
        }
        if (isArrayAssigned(arrayName, body)) {
            return false;
        }
        return true;
    }

    private static boolean isArrayAssigned(String arrayReference, PsiStatement body) {
        final ArrayAssignmentVisitor visitor = new ArrayAssignmentVisitor(arrayReference);
        body.accept(visitor);
        return visitor.isArrayAssigned();
    }

    private static boolean indexVarOnlyUsedAsIndex(String arrayName, PsiLocalVariable indexVar, PsiStatement body) {
        final IndexOnlyUsedAsIndexVisitor visitor = new IndexOnlyUsedAsIndexVisitor(arrayName, indexVar);
        body.accept(visitor);
        return visitor.isIndexVariableUsedOnlyAsIndex();
    }

    private boolean isCollectionLoopStatement(PsiForStatement forStatement) {
        final PsiStatement initialization = forStatement.getInitialization();
        if (!(initialization instanceof PsiDeclarationStatement)) {
            return false;
        }
        final PsiDeclarationStatement declaration = (PsiDeclarationStatement) initialization;
        if (declaration.getDeclaredElements().length != 1) {
            return false;
        }
        final PsiLocalVariable declaredVar = (PsiLocalVariable) declaration.getDeclaredElements()[0];

        final PsiType declaredVarType = declaredVar.getType();
        if (!(declaredVarType instanceof PsiClassType)) {
            return false;
        }
        final PsiClassType classType = (PsiClassType) declaredVarType;
        final PsiClass declaredClass = classType.resolve();
        if (declaredClass == null) {
            return false;
        }
        if (!ClassUtils.isSubclass(declaredClass, "java.util.Iterator")) {
            return false;
        }
        final PsiExpression initialValue = declaredVar.getInitializer();
        if (initialValue == null) {
            return false;
        }
        final String iteratorName = declaredVar.getName();
        final PsiExpression condition = forStatement.getCondition();
        if (!isHasNext(condition, iteratorName)) {
            return false;
        }
        final PsiStatement update = forStatement.getUpdate();
        if (update != null && !(update instanceof PsiEmptyStatement)) {
            return false;
        }
        final PsiStatement body = forStatement.getBody();
        if (body == null) {
            return false;
        }
        if (calculateCallsToIteratorNext(iteratorName, body) != 1) {
            return false;
        }
        if (isIteratorRemoveCalled(iteratorName, body)) {
            return false;
        }
        if (isIteratorAssigned(iteratorName, body)) {
            return false;
        }
        return true;
    }

    private static int calculateCallsToIteratorNext(String iteratorName, PsiStatement body) {
        final NumCallsToIteratorNextVisitor visitor = new NumCallsToIteratorNextVisitor(iteratorName);
        body.accept(visitor);
        return visitor.getNumCallsToIteratorNext();
    }

    private static boolean isIteratorAssigned(String iteratorName, PsiStatement body) {
        final IteratorAssignmentVisitor visitor = new IteratorAssignmentVisitor(iteratorName);
        body.accept(visitor);
        return visitor.isIteratorAssigned();
    }

    private static boolean isIteratorRemoveCalled(String iteratorName, PsiStatement body) {
        final IteratorRemoveVisitor visitor = new IteratorRemoveVisitor(iteratorName);
        body.accept(visitor);
        return visitor.isRemoveCalled();
    }

    private static boolean isHasNext(PsiExpression condition, String iterator) {
        if (!(condition instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression call = (PsiMethodCallExpression) condition;
        final PsiExpressionList argumentList = call.getArgumentList();
        if (argumentList == null) {
            return false;
        }
        final PsiExpression[] args = argumentList.getExpressions();
        if (args.length != 0) {
            return false;
        }
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        if (methodExpression == null) {
            return false;
        }
        final String methodName = methodExpression.getReferenceName();
        if (!"hasNext".equals(methodName)) {
            return false;
        }
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null) {
            return true;
        }
        final String target = qualifier.getText();
        if (!iterator.equals(target)) {
            return false;
        }
        return true;
    }

    private static PsiReferenceExpression getArrayFromCondition(PsiExpression condition) {
        final PsiExpression strippedCondition = ParenthesesUtils.stripParentheses(condition);
        final PsiBinaryExpression binaryExp = (PsiBinaryExpression) strippedCondition;
        final PsiExpression rhs = binaryExp.getROperand();
        final PsiReferenceExpression strippedRhs = (PsiReferenceExpression) ParenthesesUtils.stripParentheses(rhs);
        return (PsiReferenceExpression) strippedRhs.getQualifierExpression();
    }


    private static boolean isIncrement(PsiStatement statement, PsiLocalVariable var) {
        if (!(statement instanceof PsiExpressionStatement)) {
            return false;
        }
        PsiExpression exp = ((PsiExpressionStatement) statement).getExpression();
        exp = ParenthesesUtils.stripParentheses(exp);
        if (exp instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExp = (PsiPrefixExpression) exp;
            final PsiJavaToken sign = prefixExp.getOperationSign();
            if (sign == null) {
                return false;
            }
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
                return false;
            }
            final PsiExpression operand = prefixExp.getOperand();
            if (!expressionIsVariableLookup(operand, var)) {
                return false;
            }
            return true;
        } else if (exp instanceof PsiPostfixExpression) {
            final PsiPostfixExpression postfixExp = (PsiPostfixExpression) exp;
            final PsiJavaToken sign = postfixExp.getOperationSign();
            if (sign == null) {
                return false;
            }
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
                return false;
            }
            final PsiExpression operand = postfixExp.getOperand();
            if (!expressionIsVariableLookup(operand, var)) {
                return false;
            }
            return true;
        }
        return false;
    }

    private static boolean isArrayLengthComparison(PsiExpression condition, PsiLocalVariable var) {
        final PsiExpression strippedCondition = ParenthesesUtils.stripParentheses(condition);

        if (!(strippedCondition instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExp = (PsiBinaryExpression) strippedCondition;
        final PsiJavaToken sign = binaryExp.getOperationSign();
        if (sign == null) {
            return false;
        }
        if (!sign.getTokenType().equals(JavaTokenType.LT)) {
            return false;
        }
        final PsiExpression lhs = binaryExp.getLOperand();
        if (!expressionIsVariableLookup(lhs, var)) {
            return false;
        }
        final PsiExpression rhs = binaryExp.getROperand();
        if (!expressionIsArrayLengthLookup(rhs)) {
            return false;
        }
        return true;
    }

    private static boolean expressionIsArrayLengthLookup(PsiExpression expression) {
        final PsiExpression strippedExpression = ParenthesesUtils.stripParentheses(expression);
        if (!(strippedExpression instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiReferenceExpression reference = (PsiReferenceExpression) strippedExpression;
        final String referenceName = reference.getReferenceName();
        if (!"length".equals(referenceName)) {
            return false;
        }
        final PsiExpression qualifier = reference.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression)) {
            return false;
        }
        if (qualifier == null) {
            return false;
        }
        final PsiType type = qualifier.getType();
        if (type == null) {
            return false;
        }
        return type.getArrayDimensions() > 0;
    }

    private static boolean expressionIsVariableLookup(PsiExpression expression, PsiLocalVariable var) {
        final PsiExpression strippedExpression = ParenthesesUtils.stripParentheses(expression);

        final String expressionText = strippedExpression.getText();
        final String varText = var.getName();
        return expressionText.equals(varText);
    }

    private static class ArrayAssignmentVisitor extends PsiRecursiveElementVisitor {
        private boolean arrayAssigned = false;
        private final String arrayName;

        ArrayAssignmentVisitor(String arrayName) {
            super();
            this.arrayName = arrayName;
        }

        public void visitReferenceExpression(PsiReferenceExpression ref) {
            final PsiExpression qualifier = ref.getQualifierExpression();
            if (qualifier != null) {
                qualifier.accept(this);
            }
            final PsiReferenceParameterList typeParameters = ref.getParameterList();
            if (typeParameters != null) {
                typeParameters.accept(this);
            }
        }

        public void visitAssignmentExpression(PsiAssignmentExpression exp) {
            super.visitAssignmentExpression(exp);
            final PsiExpression lhs = exp.getLExpression();
            if (lhs != null) {
                if (arrayName.equals(lhs.getText())) {
                    arrayAssigned = true;
                }
            }
        }

        public boolean isArrayAssigned() {
            return arrayAssigned;
        }
    }

    private static class NumCallsToIteratorNextVisitor extends PsiRecursiveElementVisitor {
        private int numCallsToIteratorNext = 0;
        private final String iteratorName;

        NumCallsToIteratorNextVisitor(String iteratorName) {
            super();
            this.iteratorName = iteratorName;
        }


        public void visitReferenceExpression(PsiReferenceExpression ref) {
            final PsiExpression qualifier = ref.getQualifierExpression();
            if (qualifier != null) {
                qualifier.accept(this);
            }
            final PsiReferenceParameterList typeParameters = ref.getParameterList();
            if (typeParameters != null) {
                typeParameters.accept(this);
            }
        }

        public void visitMethodCallExpression(PsiMethodCallExpression callExpression) {
            super.visitMethodCallExpression(callExpression);
            final PsiReferenceExpression methodExpression =
                    callExpression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"next".equals(methodName)) {
                return;
            }

            final PsiExpression qualifier = methodExpression.getQualifierExpression();
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

    private static class IteratorAssignmentVisitor extends PsiRecursiveElementVisitor {
        private boolean iteratorAssigned = false;
        private final String iteratorName;

        IteratorAssignmentVisitor(String iteratorName) {
            super();
            this.iteratorName = iteratorName;
        }

        public void visitReferenceExpression(PsiReferenceExpression ref) {
            final PsiExpression qualifier = ref.getQualifierExpression();
            if (qualifier != null) {
                qualifier.accept(this);
            }
            final PsiReferenceParameterList typeParameters = ref.getParameterList();
            if (typeParameters != null) {
                typeParameters.accept(this);
            }
        }

        public void visitAssignmentExpression(PsiAssignmentExpression exp) {
            super.visitAssignmentExpression(exp);
            final PsiExpression lhs = exp.getLExpression();
            if (lhs != null) {
                final String lhsText = lhs.getText();
                if (iteratorName.equals(lhsText)) {
                    iteratorAssigned = true;
                }
            }
        }

        public boolean isIteratorAssigned() {
            return iteratorAssigned;
        }
    }

    private static class IteratorRemoveVisitor extends PsiRecursiveElementVisitor {
        private boolean removeCalled = false;
        private final String iteratorName;

        IteratorRemoveVisitor(String iteratorName) {
            super();
            this.iteratorName = iteratorName;
        }

        public void visitReferenceExpression(PsiReferenceExpression ref) {
            final PsiExpression qualifier = ref.getQualifierExpression();
            if (qualifier != null) {
                qualifier.accept(this);
            }
            final PsiReferenceParameterList typeParameters = ref.getParameterList();
            if (typeParameters != null) {
                typeParameters.accept(this);
            }
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final String name = methodExpression.getReferenceName();
            if (!"remove".equals(name)) {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
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

    private static class IndexOnlyUsedAsIndexVisitor extends PsiRecursiveElementVisitor {
        private boolean indexVariableUsedOnlyAsIndex = true;
        private final String arrayName;
        private final PsiLocalVariable indexVariable;

        IndexOnlyUsedAsIndexVisitor(String arrayName, PsiLocalVariable indexVariable) {
            super();
            this.arrayName = arrayName;
            this.indexVariable = indexVariable;
        }

        public void visitReferenceExpression(PsiReferenceExpression ref) {
            final PsiExpression qualifier = ref.getQualifierExpression();
            if (qualifier != null) {
                qualifier.accept(this);
            }
            final PsiReferenceParameterList typeParameters = ref.getParameterList();
            if (typeParameters != null) {
                typeParameters.accept(this);
            }
            final PsiElement element = ref.resolve();
            if (!indexVariable.equals(element)) {
                return;
            }
            final PsiElement parent = ref.getParent();
            if (!(parent instanceof PsiArrayAccessExpression)) {
                indexVariableUsedOnlyAsIndex = false;
                return;
            }
            final PsiArrayAccessExpression arrayAccess = (PsiArrayAccessExpression) parent;
            final PsiExpression arrayExpression = arrayAccess.getArrayExpression();
            if (arrayExpression == null) {
                return;
            }
            if (!arrayExpression.getText().equals(arrayName)) {
                indexVariableUsedOnlyAsIndex = false;
                return;
            }
            final PsiElement arrayExpressionContext = arrayAccess.getParent();
            if (arrayExpressionContext instanceof PsiAssignmentExpression) {
                final PsiAssignmentExpression assignment = (PsiAssignmentExpression) arrayExpressionContext;
                final PsiExpression lhs = assignment.getLExpression();
                if (lhs.equals(arrayAccess)) {
                    indexVariableUsedOnlyAsIndex = false;
                    return;
                }
            }
        }

        public boolean isIndexVariableUsedOnlyAsIndex() {
            return indexVariableUsedOnlyAsIndex;
        }
    }
}
