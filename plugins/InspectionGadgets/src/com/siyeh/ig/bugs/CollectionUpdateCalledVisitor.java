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
import com.siyeh.ig.psiutils.CollectionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

class CollectionUpdateCalledVisitor extends PsiRecursiveElementVisitor{

    /**
         * @noinspection StaticCollection
         */
    @NonNls private static final Set<String> updateNames =
            new HashSet<String>(29);
    static{
        updateNames.add("add");
        updateNames.add("addAll");
        updateNames.add("addBefore");
        updateNames.add("addElement");
        updateNames.add("addFirst");
        updateNames.add("addLast");
        updateNames.add("clear");
        updateNames.add("drainTo");
        updateNames.add("insertElementAt");
        updateNames.add("load");
        updateNames.add("loadFromXml");
        updateNames.add("offer");
        updateNames.add("push");
        updateNames.add("put");
        updateNames.add("putAll");
        updateNames.add("putIfAbsent");
        updateNames.add("remove");
        updateNames.add("removeAll");
        updateNames.add("removeAllElements");
        updateNames.add("replace");
        updateNames.add("retainAll");
        updateNames.add("removeElementAt");
        updateNames.add("removeFirst");
        updateNames.add("removeLast");
        updateNames.add("removeRange");
        updateNames.add("set");
        updateNames.add("setElementAt");
        updateNames.add("setProperty");
        updateNames.add("take");
    }

    private boolean updated = false;
    private final PsiVariable variable;

    CollectionUpdateCalledVisitor(PsiVariable variable){
        super();
        this.variable = variable;
    }

    public void visitElement(@NotNull PsiElement element){
        if(!updated){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(
            @NotNull PsiMethodCallExpression call){
        super.visitMethodCallExpression(call);
        if(updated){
            return;
        }
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if(!updateNames.contains(methodName)){
            return;
        }
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if(qualifier == null || qualifier instanceof PsiThisExpression){
            final PsiElement method = methodExpression.resolve();
            if(method == null){
                return;
            }
            final PsiClass aClass = (PsiClass)method.getParent();
            if (CollectionUtils.isCollectionClassOrInterface(aClass)){
                updated = true;
            }
        } else{
            if(!(qualifier instanceof PsiReferenceExpression)){
                return;
            }
            final PsiElement referent = ((PsiReference)qualifier).resolve();
            if(referent == null){
                return;
            }
            if(referent.equals(variable)){
                updated = true;
            }
        }
    }

    public boolean isUpdated(){
        return updated;
    }
}