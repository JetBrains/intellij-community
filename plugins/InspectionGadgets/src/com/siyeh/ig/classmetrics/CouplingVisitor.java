package com.siyeh.ig.classmetrics;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.project.Project;
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

    private final PsiClass m_class;
    private final boolean m_includeJavaClasses;
    private final boolean m_includeLibraryClasses;
    private final Set m_dependencies = new HashSet(10);

    CouplingVisitor(PsiClass aClass, boolean includeJavaClasses,
                    boolean includeLibraryClasses) {
        super();
        m_class = aClass;
        m_includeJavaClasses = includeJavaClasses;
        m_includeLibraryClasses = includeLibraryClasses;
    }

    public void visitField(PsiField field) {
        super.visitField(field);
        final PsiType type = field.getType();
        addDependency(type);
    }

    public void visitLocalVariable(PsiLocalVariable var) {
        super.visitLocalVariable(var);
        final PsiType type = var.getType();
        addDependency(type);
    }

    public void visitMethod(PsiMethod method) {
        super.visitMethod(method);
        final PsiType returnType = method.getReturnType();
        addDependency(returnType);
        addDependenciesForParameters(method);
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

    private void addDependenciesForParameters(PsiMethod method) {
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            final PsiType paramType = parameters[i].getType();
            addDependency(paramType);
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
        if (operand == null) {
            return;
        }
        final PsiType classType = operand.getType();
        addDependency(classType);
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
        if (checkType == null) {
            return;
        }
        final PsiType classType = checkType.getType();
        addDependency(classType);
    }

    public void visitTypeCastExpression(PsiTypeCastExpression exp) {
        super.visitTypeCastExpression(exp);
        final PsiTypeElement castType = exp.getCastType();
        if (castType == null) {
            return;
        }
        final PsiType classType = castType.getType();
        addDependency(classType);
    }

    private void addDependency(PsiType type) {
        if (type == null) {
            return;
        }
        final PsiType baseType = type.getDeepComponentType();
        final String baseTypeName = baseType.getCanonicalText();
        if (isPrimitiveType(baseTypeName)) {
            return;
        }
        final String qualifiedName = m_class.getQualifiedName();
        if (baseTypeName.equals(qualifiedName)) {
            return;
        }
        if (baseTypeName.startsWith(qualifiedName + '.')) {
            return;
        }
        if (!m_includeJavaClasses &&
                (baseTypeName.startsWith("java.") ||
                baseTypeName.startsWith("javax."))) {
            return;
        }
        if (!m_includeLibraryClasses) {
            final PsiManager manager = m_class.getManager();
            final Project project = manager.getProject();
            final GlobalSearchScope searchScope =
                    GlobalSearchScope.allScope(project);
            final PsiClass aClass = manager.findClass(baseTypeName, searchScope);
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
