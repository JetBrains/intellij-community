package com.siyeh.ig.threading;

import com.intellij.psi.*;

import java.util.HashSet;
import java.util.Set;

class VariableAccessVisitor extends PsiRecursiveElementVisitor {
    private final Set m_synchronizedAccesses = new HashSet(2);
    private final Set m_unsynchronizedAccesses = new HashSet(2);
    private boolean m_inInitializer = false;
    private boolean m_inSynchronizedContext = false;

    VariableAccessVisitor() {
        super();
    }

    public void visitReferenceExpression(PsiReferenceExpression ref) {
        super.visitReferenceExpression(ref);
        final PsiExpression qualifier = ref.getQualifierExpression();

        if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
            return;
        }
        final PsiElement element = ref.resolve();
        if (!(element instanceof PsiField)) {
            return;
        }
        if (m_inInitializer) {
        } else if (m_inSynchronizedContext) {
            m_synchronizedAccesses.add(element);
        } else {
            m_unsynchronizedAccesses.add(element);
        }
    }

    public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
        final boolean wasInSync = m_inSynchronizedContext;
        m_inSynchronizedContext = true;
        super.visitSynchronizedStatement(statement);
        m_inSynchronizedContext = wasInSync;
    }

    public void visitMethod(PsiMethod method) {
        final boolean methodIsSynchonized = method.hasModifierProperty(PsiModifier.SYNCHRONIZED);
        boolean wasInSync = false;
        if (methodIsSynchonized) {
            wasInSync = m_inSynchronizedContext;
            m_inSynchronizedContext = true;
        }
        final boolean isConstructor = method.isConstructor();
        if (isConstructor) {
            m_inInitializer = true;
        }
        super.visitMethod(method);
        if (methodIsSynchonized) {
            m_inSynchronizedContext = wasInSync;
        }
        if (isConstructor) {
            m_inInitializer = false;
        }
    }

    public void visitClassInitializer(PsiClassInitializer initializer) {
        m_inInitializer = true;
        super.visitClassInitializer(initializer);
        m_inInitializer = false;
    }

    public void visitField(PsiField field) {
        m_inInitializer = true;
        super.visitField(field);    //To change body of overriden methods use Options | File Templates.
        m_inInitializer = false;
    }

    public Set getInappropriatelyAccessedFields() {
        final Set out = new HashSet(m_synchronizedAccesses);
        out.retainAll(m_unsynchronizedAccesses);
        return out;
    }

}
