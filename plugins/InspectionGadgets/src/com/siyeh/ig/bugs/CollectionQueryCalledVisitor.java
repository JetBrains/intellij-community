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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

class CollectionQueryCalledVisitor extends PsiRecursiveElementVisitor{

    /**
         * @noinspection StaticCollection
         */
    @NonNls private static final Set<String> queryNames = new HashSet<String>(33);
    static{
        queryNames.add("clone");
        queryNames.add("contains");
        queryNames.add("containsAll");
        queryNames.add("containsKey");
        queryNames.add("containsValue");
        queryNames.add("copyInto");
        queryNames.add("entrySet");
        queryNames.add("elements");
        queryNames.add("empty");
        queryNames.add("enumeration");
        queryNames.add("firstElement");
        queryNames.add("get");
        queryNames.add("getFirst");
        queryNames.add("getLast");
        queryNames.add("getProperty");
        queryNames.add("indexOf");
        queryNames.add("isEmpty");
        queryNames.add("iterator");
        queryNames.add("keys");
        queryNames.add("keySet");
        queryNames.add("lastElement");
        queryNames.add("lastIndexOf");
        queryNames.add("peek");
        queryNames.add("poll");
        queryNames.add("pop");
        queryNames.add("propertyNames");
        queryNames.add("save");
        queryNames.add("size");
        queryNames.add("store");
        queryNames.add("storeToXml");
        queryNames.add("subList");
        queryNames.add("toArray");
        queryNames.add("values");
    }

    private boolean queried = false;
    private final PsiVariable variable;

    CollectionQueryCalledVisitor(PsiVariable variable){
        super();
        this.variable = variable;
    }

    public void visitElement(@NotNull PsiElement element){
        if(!queried){
            super.visitElement(element);
        }
    }

    public void visitForeachStatement(@NotNull PsiForeachStatement statement){
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

    public void visitMethodCallExpression(
            @NotNull PsiMethodCallExpression call){
        if(queried){
            return;
        }
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if(!(qualifier instanceof PsiReferenceExpression)){
            return;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression)qualifier;
        final PsiElement referent = referenceExpression.resolve();
        if(referent == null){
            return;
        }
        if(!referent.equals(variable)){
            return;
        }
        final boolean isStatement =
                call.getParent() instanceof PsiExpressionStatement;
        if(!isStatement){
            // this gets the cases where the return values of updates
            // are used as an implicit query
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