package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ExceptionUtils {
    private ExceptionUtils() {
        super();
    }

    private static final Set s_genericExceptionTypes = new HashSet(4);

    static {
        s_genericExceptionTypes.add("java.lang.Throwable");
        s_genericExceptionTypes.add("java.lang.Exception");
        s_genericExceptionTypes.add("java.lang.RuntimeException");
        s_genericExceptionTypes.add("java.lang.Error");
    }

    private static Set getExceptionTypesHandled(PsiTryStatement statement) {
        final Set out = new HashSet(5);
        final PsiParameter[] params = statement.getCatchBlockParameters();
        for (int i = 0; i < params.length; i++) {
            final PsiType type = params[i].getType();
            out.add(type);
        }
        return out;
    }

    public static Set calculateExceptionsThrown(PsiElement statement, PsiElementFactory factory) {
        final ExceptionsThrownVisitor visitor = new ExceptionsThrownVisitor(factory);
        statement.accept(visitor);
        return visitor.getExceptionsThrown();
    }

    public static boolean isGenericExceptionClass(PsiType exceptionType) {
        if (exceptionType == null) {
            return false;
        }
        final String className = exceptionType.getCanonicalText();
        return s_genericExceptionTypes.contains(className);

    }

    private static class ExceptionsThrownVisitor extends PsiRecursiveElementVisitor {
        private final PsiElementFactory m_factory;
        private final Set m_exceptionsThrown = new HashSet(4);

        private ExceptionsThrownVisitor(PsiElementFactory factory) {
            super();
            m_factory = factory;
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiReferenceList throwsList = method.getThrowsList();
            if (throwsList == null) {
                return;
            }
            final PsiJavaCodeReferenceElement[] list = throwsList.getReferenceElements();
            for (int i = 0; i < list.length; i++) {
                final PsiJavaCodeReferenceElement referenceElement = list[i];
                final PsiClass exceptionClass = (PsiClass) referenceElement.resolve();
                if (exceptionClass != null) {
                    final PsiClassType exceptionType = m_factory.createType(exceptionClass);
                    m_exceptionsThrown.add(exceptionType);
                }
            }
        }

        public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiReferenceList throwsList = method.getThrowsList();
            if (throwsList == null) {
                return;
            }
            final PsiJavaCodeReferenceElement[] list = throwsList.getReferenceElements();
            for (int i = 0; i < list.length; i++) {
                final PsiJavaCodeReferenceElement referenceElement = list[i];
                final PsiClass exceptionClass = (PsiClass) referenceElement.resolve();
                if (exceptionClass != null) {
                    final PsiClassType exceptionType = m_factory.createType(exceptionClass);
                    m_exceptionsThrown.add(exceptionType);
                }
            }
        }

        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            final PsiExpression exception = statement.getException();
            if (exception == null) {
                return;
            }
            final PsiType type = exception.getType();
            if (type == null) {
                return;
            }
            m_exceptionsThrown.add(type);
        }

        public void visitTryStatement(PsiTryStatement statement) {
            final PsiCodeBlock tryBlock = statement.getTryBlock();
            final PsiElementFactory factory = m_factory;
            final Set exceptionsThrown = m_exceptionsThrown;
            if (tryBlock != null) {
                final Set tryExceptions = calculateExceptionsThrown(tryBlock, factory);
                final Set exceptionsHandled = ExceptionUtils.getExceptionTypesHandled(statement);
                for (Iterator thrown = tryExceptions.iterator(); thrown.hasNext();) {
                    final PsiType thrownType = (PsiType) thrown.next();
                    if (!isExceptionHandled(exceptionsHandled, thrownType)) {
                        exceptionsThrown.add(thrownType);
                    }
                }
            }
            final PsiCodeBlock finallyBlock = statement.getFinallyBlock();
            if (finallyBlock != null) {
                final Set finallyExceptions = calculateExceptionsThrown(finallyBlock, factory);
                exceptionsThrown.addAll(finallyExceptions);
            }

            final PsiCodeBlock[] catchBlocks = statement.getCatchBlocks();
            for (int i = 0; i < catchBlocks.length; i++) {
                final Set catchExceptions = calculateExceptionsThrown(catchBlocks[i], factory);
                exceptionsThrown.addAll(catchExceptions);
            }
        }

        private static boolean isExceptionHandled(Set exceptionHandled, PsiType thrownType) {
            for (Iterator handled = exceptionHandled.iterator(); handled.hasNext();) {
                final PsiType handledType = (PsiType) handled.next();
                if (handledType.isAssignableFrom(thrownType)) {
                    return true;
                }
            }
            return false;
        }

        private Set getExceptionsThrown() {
            return Collections.unmodifiableSet(m_exceptionsThrown);
        }
    }
}