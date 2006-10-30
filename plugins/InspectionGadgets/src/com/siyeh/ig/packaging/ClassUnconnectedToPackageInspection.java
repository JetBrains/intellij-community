package com.siyeh.ig.packaging;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.dependency.DependencyUtils;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ClassUnconnectedToPackageInspection extends BaseGlobalInspection {

    public String getGroupDisplayName() {
        return GroupNames.PACKAGING_GROUP_NAME;
    }

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
        final RefUtil refUtil = RefUtil.getInstance();
        final RefPackage package1 = refUtil.getPackage(class1);
        final RefPackage package2= refUtil.getPackage(class2);
        if(package1 == null || package2 == null)
        {
            return false;
        }
        final String name1 = package1.getQualifiedName();
        final String name2 = package2.getQualifiedName();
        return name1 != null && name2 != null && name1.equals(name2);
    }

}
