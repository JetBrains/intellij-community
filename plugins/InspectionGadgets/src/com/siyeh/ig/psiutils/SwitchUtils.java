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
package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchStatement;
import org.jetbrains.annotations.NotNull;

public class SwitchUtils{

    private SwitchUtils(){
        super();
    }

    public static int calculateBranchCount(
            @NotNull PsiSwitchStatement statement){
        final PsiCodeBlock body = statement.getBody();
        int branches = 0;
        if (body == null) {
            return branches;
        }
        final PsiStatement[] statements = body.getStatements();
        for(final PsiStatement child : statements){
            if(child instanceof PsiSwitchLabelStatement){
                branches++;
            }
        }
        return branches;
    }
}
