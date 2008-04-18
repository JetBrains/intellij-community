/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.validation;

import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.psi.*;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// TODO: more detailed error descriptions

@SuppressWarnings({"SimplifiableIfStatement"})
class XsltPatternValidator {
    private XsltPatternValidator() {
    }

    public static void validate(AnnotationHolder annotationHolder, PsiFile file) {
        PsiElement firstChild = file.getFirstChild();
        if (firstChild != null) {
            if (!checkPattern(skipWhitespace(firstChild))) {
                annotationHolder.createErrorAnnotation(firstChild, "Bad pattern");
            }
        } else {
            annotationHolder.createErrorAnnotation(TextRange.from(0, 1), "Missing pattern");
        }
    }

    private static boolean checkPattern(PsiElement element) {
        if (checkLocationPathPattern(element)) {
            return true;
        }

        if (element instanceof XPathBinaryExpression) {
            final XPathBinaryExpression expression = (XPathBinaryExpression)element;
            if (expression.getOperator() == XPathTokenTypes.UNION) {
                if (checkPattern(expression.getLOperand()) && checkLocationPathPattern(expression.getROperand())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean checkLocationPathPattern(PsiElement element) {
        if (element instanceof XPathLocationPath) {
            final XPathLocationPath locationPath = (XPathLocationPath)element;
            final XPathExpression[] steps = getSteps(locationPath);

            if (locationPath.isAbsolute()) return checkRelativePath(steps, 0);

            PsiElement child = skipWhitespace(locationPath.getFirstChild());
            if (child == null) return true;

            if (!checkIdKeyPattern(child)) {
                return checkRelativePath(steps, 0);
            }

            if ((child = skipWhitespace(child.getNextSibling())) == null) return true;

            if (!checkPathOp(child)) return false;

            child = skipWhitespace(child.getNextSibling());
            if (child == null) return false;
            if (!(child instanceof XPathStep)) return false;

            return checkRelativePath(steps, 1);
        }
        return false;
    }

    private static XPathExpression[] getSteps(XPathLocationPath locationPath) {
        final XPathExpression step = locationPath.getPathExpression();

        List<XPathExpression> steps = new ArrayList<XPathExpression>();
        if (step instanceof XPathStep) {
            XPathExpression pathStep = step;
            do {
                steps.add(pathStep);
                pathStep = ((XPathStep)pathStep).getStepExpression();
            } while (pathStep instanceof XPathStep);
            if (pathStep != null) steps.add(pathStep);

            Collections.reverse(steps);
            return steps.toArray(new XPathExpression[steps.size()]);
        } else if (step != null) {
            return new XPathExpression[]{ step };
        } else {
            return XPathExpression.EMPTY_ARRAY;
        }
    }

    private static boolean checkRelativePath(XPathExpression[] element, int index) {
        for (int i = index; i < element.length; i++) {
            if (element[i] instanceof XPathLocationPath) {
                if (!checkLocationPathPattern(element[i])) {
                    return false;
                }
            } else if (!checkStepPattern(element[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkStepPattern(XPathExpression expression) {
        if (expression instanceof XPathStep) {
            final XPathStep step = (XPathStep)expression;
            final XPathAxisSpecifier axisSpecifier = step.getAxisSpecifier();

            boolean b = false;
            if (axisSpecifier != null) {
                if ((axisSpecifier.getAxis() == Axis.CHILD || axisSpecifier.getAxis() == Axis.ATTRIBUTE)) {
                    b = true;
                }
            } else {
                b = step.isAbsolute();
            }
            if (b) {
                final XPathPredicate[] predicates = step.getPredicates();
                for (XPathPredicate predicate : predicates) {
                    final XPathExpression predicateExpression = predicate.getPredicateExpression();
                    // null predicate as in "foo[]" is a syntax error and already flagged by the parser
                    b = b && (predicateExpression == null || checkPredicate(predicateExpression));
                }
            }
            return b;
        }
        return false;
    }

    private static boolean checkPredicate(@NotNull XPathExpression predicateExpression) {
        final boolean[] b = new boolean[]{ true };
        predicateExpression.accept(new PsiRecursiveElementVisitor() {
            public void visitElement(PsiElement element) {
                if (element instanceof XPathVariableReference) {
                    b[0] = false;
                } else {
                    super.visitElement(element);
                }
            }
        });
        return b[0];
    }

    private static boolean checkPathOp(PsiElement child) {
        if (child instanceof XPathToken) {
            final XPathToken token = (XPathToken)child;
            if (XPathTokenTypes.PATH_OPS.contains(token.getTokenType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkIdKeyPattern(PsiElement child) {
        if (child instanceof XPathFunctionCall) {
            final XPathFunctionCall call = (XPathFunctionCall)child;
            final XPathExpression[] arguments = call.getArgumentList();
            if ("id".equals(call.getFunctionName())) {
                if (arguments.length != 1) return false;
                return isStringLiteral(arguments[0]);
            } else if ("key".equals(call.getFunctionName())) {
                if (arguments.length != 2) return false;
                return isStringLiteral(arguments[0]) && isStringLiteral(arguments[1]);
            }
        }
        return false;
    }

    private static boolean isStringLiteral(XPathExpression argument) {
        return argument instanceof XPathString;
    }

    @Nullable
    private static PsiElement skipWhitespace(PsiElement element) {
        while (element instanceof PsiWhiteSpace) {
            element = element.getNextSibling();
        }
        return element;
    }
}