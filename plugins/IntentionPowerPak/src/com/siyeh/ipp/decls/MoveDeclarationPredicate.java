/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ipp.decls;

import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class MoveDeclarationPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(@NotNull PsiElement element){
        if(!(element instanceof PsiLocalVariable)){
            return false;
        }
        final PsiLocalVariable variable = (PsiLocalVariable) element;
        final PsiExpression initializer = variable.getInitializer();
        if(initializer != null){
            return false;
        }
        final PsiCodeBlock variableBlock =
                PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        if(variableBlock == null){
            return false;
        }
        final PsiManager manager = variable.getManager();
        final PsiSearchHelper searchHelper = manager.getSearchHelper();
        final PsiReference[] references =
                searchHelper.findReferences(variable, variable.getUseScope(),
                                            false);
        if(references.length == 0){
            return false;
        }
        final PsiElement tightestBlock = getTightestBlock(references);
        if(tightestBlock == null){
            return false;
        }
        if(!variableBlock.equals(tightestBlock)){
            return true;
        }

        final PsiReference firstReference = references[0];
        final PsiElement referenceElement = firstReference.getElement();
        if(referenceElement == null){
            return false;
        }
        final PsiElement child = getChildWhichContainsElement(variableBlock,
                                                              referenceElement);
        if(child == null){
            return false;
        }
        PsiElement prevSibling = child.getPrevSibling();
        if(prevSibling instanceof PsiWhiteSpace){
            prevSibling = prevSibling.getPrevSibling();
        }
        if(prevSibling == null){
            return false;
        }
        return !prevSibling.equals(variable.getParent());
    }

    @Nullable
    public static PsiElement getChildWhichContainsElement(
            @NotNull PsiCodeBlock ancestor, @NotNull PsiElement descendant) {
        PsiElement element = descendant;
        while(!element.equals(ancestor)){
            descendant = element;
            element = descendant.getParent();
            if(element == null){
                return null;
            }
        }
        return descendant;
    }

    @Nullable
    public static PsiCodeBlock getTightestBlock(
            @NotNull PsiReference[] references) {
        PsiCodeBlock commonParentBlock = null;
        for(PsiReference reference : references){
            final PsiElement referenceElement = reference.getElement();
            final PsiCodeBlock block =
                    PsiTreeUtil.getParentOfType(referenceElement,
                                                PsiCodeBlock.class);
            if (block == null) {
                return commonParentBlock;
            }
            if(commonParentBlock != null && !commonParentBlock.equals(block)){
                final PsiElement commonParent =
                        PsiTreeUtil.findCommonParent(commonParentBlock, block);
                if(commonParent instanceof PsiCodeBlock){
                    commonParentBlock = (PsiCodeBlock) commonParent;
                } else{
                    commonParentBlock =
                            PsiTreeUtil.getParentOfType(commonParent,
                                                        PsiCodeBlock.class);
                }
            } else{
                commonParentBlock = block;
            }
        }
        return commonParentBlock;
    }
}
