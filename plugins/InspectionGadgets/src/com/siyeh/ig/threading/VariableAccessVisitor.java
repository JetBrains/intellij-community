package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;                                                           

class VariableAccessVisitor extends PsiRecursiveElementVisitor{
    private final Set<PsiElement> m_synchronizedAccesses = new HashSet<PsiElement>(
            2);
    private final Set<PsiElement> m_unsynchronizedAccesses = new HashSet<PsiElement>(
            2);
    private boolean m_inInitializer = false;
    private boolean m_inSynchronizedContext = false;

    VariableAccessVisitor(){
        super();
    }

    public void visitReferenceExpression(@NotNull PsiReferenceExpression ref){
        super.visitReferenceExpression(ref);
        final PsiExpression qualifier = ref.getQualifierExpression();

        if(qualifier != null && !(qualifier instanceof PsiThisExpression)){
            return;
        }
        final PsiElement element = ref.resolve();
        if(!(element instanceof PsiField)){
            return;
        }
        if(m_inInitializer){
        } else if(m_inSynchronizedContext){
            m_synchronizedAccesses.add(element);
        } else{
            m_unsynchronizedAccesses.add(element);
        }
    }

    public void visitSynchronizedStatement(
            @NotNull PsiSynchronizedStatement statement){
        final boolean wasInSync = m_inSynchronizedContext;
        m_inSynchronizedContext = true;
        super.visitSynchronizedStatement(statement);
        m_inSynchronizedContext = wasInSync;
    }

    public void visitMethod(@NotNull PsiMethod method){
        final boolean methodIsSynchonized =
                method.hasModifierProperty(PsiModifier.SYNCHRONIZED)
                        || methodIsAlwaysUsedSynchronized(method);
        boolean wasInSync = false;
        if(methodIsSynchonized){
            wasInSync = m_inSynchronizedContext;
            m_inSynchronizedContext = true;
        }
        final boolean isConstructor = method.isConstructor();
        if(isConstructor){
            m_inInitializer = true;
        }
        super.visitMethod(method);
        if(methodIsSynchonized){
            m_inSynchronizedContext = wasInSync;
        }
        if(isConstructor){
            m_inInitializer = false;
        }
    }

    private boolean methodIsAlwaysUsedSynchronized(PsiMethod method){
        return methodIsAlwaysUsedSynchronized(method,
                                              new HashSet<PsiMethod>(),
                                              new HashSet<PsiMethod>(),
                                              new HashSet<PsiMethod>());
    }

    private boolean methodIsAlwaysUsedSynchronized(PsiMethod method,
                                                   Set<PsiMethod> methodsAlwaysSynchronized,
                                                   Set<PsiMethod> methodsNotAlwaysSynchronized,
                                                   Set<PsiMethod> pendingMethods)
    {
        if(methodsAlwaysSynchronized.contains(method)){
            return true;
        }
        if(methodsNotAlwaysSynchronized.contains(method)){
            return false;
        }
        if(!method.hasModifierProperty(PsiModifier.PRIVATE)){
            return false;
        }
        if(pendingMethods.contains(method))
        {
            return true;
        }
        pendingMethods.add(method);
        final PsiManager manager = method.getManager();
        final PsiSearchHelper searchHelper = manager.getSearchHelper();
        final SearchScope scope = method.getUseScope();
        final PsiReference[] references = searchHelper
                .findReferences(method, scope, true);
        for(PsiReference reference : references){
            if(!isInSynchronizedContext(reference,
                                        methodsAlwaysSynchronized,
                                        methodsNotAlwaysSynchronized,
                                        pendingMethods)){
                methodsNotAlwaysSynchronized.add(method);
                pendingMethods.remove(method);
                return false;
            }
        }
        methodsAlwaysSynchronized.add(method);
        pendingMethods.remove(method);
        return true;
    }

    private boolean isInSynchronizedContext(PsiReference reference,
                                            Set<PsiMethod> methodsAlwaysSynchronized,
                                            Set<PsiMethod> methodsNotAlwaysSynchronized,
                                            Set<PsiMethod> pendingMethods)
    {
        final PsiElement element = reference.getElement();
        if(PsiTreeUtil.getParentOfType(element,
                                       PsiSynchronizedStatement.class) != null){
            return true;
        }
        final PsiMethod method =
                PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if(method == null){
            return false;
        }
        if(method.hasModifierProperty(PsiModifier.SYNCHRONIZED)){
            return true;
        }
        return methodIsAlwaysUsedSynchronized(method, methodsAlwaysSynchronized,
                                              methodsNotAlwaysSynchronized,
                                              pendingMethods);
    }

    public void visitClassInitializer(@NotNull PsiClassInitializer initializer){
        m_inInitializer = true;
        super.visitClassInitializer(initializer);
        m_inInitializer = false;
    }

    public void visitField(@NotNull PsiField field){
        m_inInitializer = true;
        super.visitField(field);
        m_inInitializer = false;
    }

    public Set<PsiElement> getInappropriatelyAccessedFields(){
        final Set<PsiElement> out = new HashSet<PsiElement>(
                m_synchronizedAccesses);
        out.retainAll(m_unsynchronizedAccesses);
        return out;
    }
}
