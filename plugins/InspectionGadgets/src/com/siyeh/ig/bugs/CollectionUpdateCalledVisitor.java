package com.siyeh.ig.bugs;

import com.intellij.psi.*;

import java.util.HashSet;
import java.util.Set;

class CollectionUpdateCalledVisitor extends PsiRecursiveElementVisitor{
    /**
         * @noinspection StaticCollection
         */
    private static final Set<String> updateNames = new HashSet<String>(10);

    static{
        updateNames.add("add");
        updateNames.add("put");
        updateNames.add("set");
        updateNames.add("remove");
        updateNames.add("addAll");
        updateNames.add("removeAll");
        updateNames.add("retainAll");
        updateNames.add("putAll");
        updateNames.add("clear");
        updateNames.add("addElement");
        updateNames.add("removeAllElements");
        updateNames.add("trimToSize");
        updateNames.add("removeElementAt");
        updateNames.add("removeRange");
        updateNames.add("insertElementAt");
        updateNames.add("setElementAt");
        updateNames.add("removeRange");
        updateNames.add("addFirst");
        updateNames.add("addLast");
        updateNames.add("addBefore");
        updateNames.add("removeFirst");
        updateNames.add("removeLast");
        updateNames.add("offer");
    }

    private boolean updated = false;
    private final PsiVariable variable;

    CollectionUpdateCalledVisitor(PsiVariable variable){
        super();
        this.variable = variable;
    }

    public void visitElement(PsiElement element){
        if(!updated){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression call){
        super.visitMethodCallExpression(call);
        if(updated){
            return;
        }
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        if(methodExpression == null){
            return;
        }
        final String methodName = methodExpression.getReferenceName();
        if(!updateNames.contains(methodName)){
            return;
        }
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if(!(qualifier instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) qualifier).resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            updated = true;
        }
    }

    public boolean isUpdated(){
        return updated;
    }
}
