package com.siyeh.ig.bugs;

import com.intellij.psi.*;

import java.util.HashSet;
import java.util.Set;

class CollectionQueryCalledVisitor extends PsiRecursiveElementVisitor{
    /**
         * @noinspection StaticCollection
         */
    private static final Set<String> queryNames = new HashSet<String>(10);

    static{
        queryNames.add("get");
        queryNames.add("contains");
        queryNames.add("containsKey");
        queryNames.add("containsValue");
        queryNames.add("containsAll");
        queryNames.add("size");
        queryNames.add("indexOf");
        queryNames.add("iterator");
        queryNames.add("lastIndexOf");
        queryNames.add("toArray");
        queryNames.add("isEmpty");
        queryNames.add("entrySet");
        queryNames.add("keySet");
        queryNames.add("values");
        queryNames.add("keys");
        queryNames.add("elements");
        queryNames.add("subList");
        queryNames.add("copyInto");
        queryNames.add("lastElement");
        queryNames.add("firstElement");
        queryNames.add("clone");
        queryNames.add("getFirst");
        queryNames.add("getLast");
        queryNames.add("peek");
        queryNames.add("poll");
    }

    private boolean queried = false;
    private final PsiVariable variable;

    CollectionQueryCalledVisitor(PsiVariable variable){
        super();
        this.variable = variable;
    }

    public void visitElement(PsiElement element){
        if(!queried){
            super.visitElement(element);
        }
    }

    public void visitForeachStatement(PsiForeachStatement statement){
        if(queried){
            return;
        }
        super.visitForeachStatement(statement);
        final PsiExpression qualifier = statement.getIteratedValue();
        if(!(qualifier instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) qualifier).resolve();
        if(referent == null){
            return;
        }
        if(!referent.equals(variable)){
            return;
        }
        queried = true;
    }

    public void visitMethodCallExpression(PsiMethodCallExpression call){
        if(queried){
            return;
        }
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        if(methodExpression == null){
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
        if(!referent.equals(variable)){
            return;
        }
        final boolean isStatement =
                        call.getParent() instanceof PsiExpressionStatement;
        if(!isStatement){
            // this gets the cases wher you're using the return values of updates
            // as an implicit query
            queried = true;
        }
        final String methodName = methodExpression.getReferenceName();
        if(queryNames.contains(methodName)){
            queried = true;
        }
    }

    public boolean isQueried(){
        return queried;
    }
}
