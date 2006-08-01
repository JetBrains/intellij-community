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
package com.siyeh.ig.classmetrics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiTypeParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ClassInheritanceDepthInspection
        extends ClassMetricInspection{

    public String getID(){
        return "ClassTooDeepInInheritanceTree";
    }

    private static final int CLASS_INHERITANCE_LIMIT = 2;

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("class.too.deep.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.CLASSMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit(){
        return CLASS_INHERITANCE_LIMIT;
    }

    protected String getConfigurationLabel(){
        return InspectionGadgetsBundle.message(
                "class.too.deep.inheritance.depth.limit.option");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final Integer count = (Integer)infos[0];
        return InspectionGadgetsBundle.message(
                "class.too.deep.problem.descriptor", count);
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ClassNestingLevel();
    }

    private class ClassNestingLevel extends BaseInspectionVisitor{

        public void visitClass(@NotNull PsiClass aClass){
            // note: no call to super
            if(aClass.isEnum()){
                return;
            }
            if(aClass instanceof PsiTypeParameter) {
                return;
            }
            final int inheritanceDepth =
                    getInheritanceDepth(aClass, new HashSet<PsiClass>());
            if (inheritanceDepth <= getLimit()){
                return;
            }
            registerClassError(aClass, Integer.valueOf(inheritanceDepth));
        }

        private int getInheritanceDepth(PsiClass aClass, Set<PsiClass> visited){
            if(visited.contains(aClass)){
                return 0;
            }
            visited.add(aClass);
            final PsiClass superClass = aClass.getSuperClass();
            if(superClass == null){
                return 0;
            }
            if(LibraryUtil.classIsInLibrary(aClass) &&
                    LibraryUtil.classIsInLibrary(superClass)){
                return 0;
            }
            return getInheritanceDepth(superClass, visited) + 1;
        }
    }
}