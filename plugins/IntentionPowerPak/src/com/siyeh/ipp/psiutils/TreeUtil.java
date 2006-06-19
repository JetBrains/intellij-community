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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class TreeUtil{

    private TreeUtil(){
        super();
    }

    @Nullable
    public static PsiElement getNextLeaf(PsiElement element){
        if(element == null){
            return null;
        }
        final PsiElement sibling = element.getNextSibling();
        if(sibling == null){
            final PsiElement parent = element.getParent();
            return getNextLeaf(parent);
        }
        return getFirstLeaf(sibling);
    }

    private static PsiElement getFirstLeaf(PsiElement element){
        final PsiElement[] children = element.getChildren();
        if(children.length == 0){
            return element;
        }
        return getFirstLeaf(children[0]);
    }

    @Nullable
    public static PsiElement getPrevLeaf(PsiElement element){
        if(element == null){
            return null;
        }
        final PsiElement sibling = element.getPrevSibling();
        if(sibling == null){
            final PsiElement parent = element.getParent();
            return getPrevLeaf(parent);
        }
        return getLastLeaf(sibling);
    }

    private static PsiElement getLastLeaf(PsiElement element){
        final PsiElement[] children = element.getChildren();
        if(children.length == 0){
            return element;
        }
        return getLastLeaf(children[children.length-1]);
    }
}