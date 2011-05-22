/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.siyeh.ig.packaging;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefJavaUtil;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.dependency.DependencyUtils;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ClassUnconnectedToPackageInspection extends BaseGlobalInspection {

    @Nullable
    public CommonProblemDescriptor[] checkElement(RefEntity refEntity,
                                                  AnalysisScope analysisScope,
                                                  InspectionManager inspectionManager,
                                                  GlobalInspectionContext globalInspectionContext) {
        if (!(refEntity instanceof RefClass)) {
            return null;
        }
        final RefClass refClass = (RefClass) refEntity;
        final PsiClass aClass = refClass.getElement();
        if (ClassUtils.isInnerClass(aClass)) {
            return null;
        }

        final Set<RefClass> dependencies =
                DependencyUtils.calculateDependenciesForClass(refClass);
        for (RefClass dependency : dependencies) {
             if(inSamePackage(refClass, dependency))
             {
                 return null;
             }
        }

        final Set<RefClass> dependents =
                DependencyUtils.calculateDependentsForClass(refClass);
        for (RefClass dependent : dependents) {
             if(inSamePackage(refClass, dependent))
             {
                 return null;
             }
        }

        final String errorString =
                InspectionGadgetsBundle.message("class.unconnected.to.package.problem.descriptor", refEntity.getName());

        return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
    }

    private static boolean inSamePackage(RefClass class1, RefClass class2) {
        final RefPackage package1 = RefJavaUtil.getPackage(class1);
        final RefPackage package2= RefJavaUtil.getPackage(class2);
        if(package1 == null || package2 == null)
        {
            return false;
        }
        final String name1 = package1.getQualifiedName();
        final String name2 = package2.getQualifiedName();
        return name1 != null && name2 != null && name1.equals(name2);
    }

}
