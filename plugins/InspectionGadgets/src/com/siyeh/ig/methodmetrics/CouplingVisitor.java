package com.siyeh.ig.methodmetrics;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.LibraryUtil;

import java.util.HashSet;
import java.util.Set;

class CouplingVisitor extends PsiRecursiveElementVisitor {
    private static final Set s_primitiveTypes = new HashSet(8);
    private boolean m_inClass = false;

    static {
        s_primitiveTypes.add("boolean");
        s_primitiveTypes.add("byte");
        s_primitiveTypes.add("char");
        s_primitiveTypes.add("short");
        s_primitiveTypes.add("int");
        s_primitiveTypes.add("long");
        s_primitiveTypes.add("float");
        s_primitiveTypes.add("double");
    }

    private static boolean isPrimitiveType(String typeName) {
        return s_primitiveTypes.contains(typeName);
    }

    private final PsiMethod m_method;
    private final boolean m_includeJavaClasses;
    private final boolean m_includeLibraryClasses;
    private final Set m_dependencies = new HashSet(10);

    CouplingVisitor(PsiMethod method, boolean includeJavaClasses,
                    boolean includeLibraryClasses) {
        super();
        m_method = method;
        m_includeJavaClasses = includeJavaClasses;
        m_includeLibraryClasses = includeLibraryClasses;
    }

    public void visitVariable(PsiVariable variable) {
        super.visitVariable(variable);
        final PsiType type = variable.getType();
        addDependency(type);
    }

    public void visitMethod(PsiMethod method) {
        super.visitMethod(method);
        final PsiType returnType = method.getReturnType();
        addDependency(returnType);
        addDependenciesForThrowsList(method);
    }

    private void addDependenciesForThrowsList(PsiMethod method) {
        final PsiReferenceList throwsList = method.getThrowsList();
        if (throwsList == null) {
            return;
        }
        final PsiClassType[] throwsTypes = throwsList.getReferencedTypes();
        for (int i = 0; i < throwsTypes.length; i++) {
            addDependency(throwsTypes[i]);
        }
    }

    public void visitNewExpression(PsiNewExpression exp) {
        super.visitNewExpression(exp);
        final PsiType classType = exp.getType();
        addDependency(classType);
    }

    public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression exp) {
        super.visitClassObjectAccessExpression(exp);
        final PsiTypeElement operand = exp.getOperand();
        addDependency(operand);
    }

    public void visitClass(PsiClass aClass) {
        final boolean wasInClass = m_inClass;
        if (!m_inClass) {

            m_inClass = true;
            super.visitClass(aClass);
        }
        m_inClass = wasInClass;
        final PsiType[] superTypes = aClass.getSuperTypes();
        for (int i = 0; i < superTypes.length; i++) {
            addDependency(superTypes[i]);
        }
    }

    public void visitTryStatement(PsiTryStatement statement) {
        super.visitTryStatement(statement);
        final PsiParameter[] catchBlockParameters = statement.getCatchBlockParameters();
        for (int i = 0; i < catchBlockParameters.length; i++) {
            final PsiType catchType = catchBlockParameters[i].getType();
            addDependency(catchType);
        }
    }

    public void visitInstanceOfExpression(PsiInstanceOfExpression exp) {
        super.visitInstanceOfExpression(exp);
        final PsiTypeElement checkType = exp.getCheckType();
        addDependency(checkType);
    }

    public void visitTypeCastExpression(PsiTypeCastExpression exp) {
        super.visitTypeCastExpression(exp);
        final PsiTypeElement castType = exp.getCastType();
        addDependency(castType);
    }

    private void addDependency(PsiTypeElement typeElement) {
        if (typeElement == null) {
            return;
        }
        final PsiType type = typeElement.getType();
        addDependency(type);
    }

    private void addDependency(PsiType type) {
        if (type == null) {
            return;
        }
        final PsiType baseType = type.getDeepComponentType();
        if (ClassUtils.isPrimitive(type)) {
            return;
        }
        final PsiClass containingClass = m_method.getContainingClass();
        final String qualifiedName = containingClass.getQualifiedName();
        if(qualifiedName == null)
        {
            return;
        }
        if (baseType.equalsToText(qualifiedName)) {
            return;
        }
        final String baseTypeName = baseType.getCanonicalText();
        if (!m_includeJavaClasses &&
                    (baseTypeName.startsWith("java.") ||
                    baseTypeName.startsWith("javax."))) {
            return;
        }
        if (baseTypeName.startsWith(qualifiedName + '.')) {
            return;
        }
        if (!m_includeLibraryClasses) {
            final PsiManager manager = m_method.getManager();
            final Project project = manager.getProject();
            final GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);
            final PsiClass aClass = manager.findClass(baseTypeName,
                    searchScope);
            if (aClass == null) {
                return;
            }
            if (LibraryUtil.classIsInLibrary(aClass)) {
                return;
            }
        }
        m_dependencies.add(baseTypeName);
    }

    public int getNumDependencies() {
        return m_dependencies.size();
    }

}
