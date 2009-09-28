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
package com.siyeh.ig.controlflow;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LocalVariableAccessVisitor extends JavaRecursiveElementVisitor {

    private final Set<PsiElement> m_accesssedVariables =
            new HashSet<PsiElement>(2);

    @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
        super.visitReferenceExpression(ref);
        final PsiExpression qualifier = ref.getQualifierExpression();
        if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
            return;
        }
        final PsiElement element = ref.resolve();
        if (!(element instanceof PsiLocalVariable)) {
            return;
        }
        m_accesssedVariables.add(element);
    }

    public Set<PsiElement> getAccessedVariables() {
        return Collections.unmodifiableSet(m_accesssedVariables);
    }
}