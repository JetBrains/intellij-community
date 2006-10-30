package com.siyeh.ig.dependency;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class CyclicClassDependencyInspection extends BaseGlobalInspection {

    public String getGroupDisplayName() {
        return GroupNames.DEPENDENCY_GROUP_NAME;
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
        if (aClass.getContainingClass() != null) {
            return null;
        }
        final Set<RefClass> dependencies =
                DependencyUtils.calculateTransitiveDependenciesForClass(refClass);
        final Set<RefClass> dependents =
                DependencyUtils.calculateTransitiveDependentsForClass(refClass);
        final Set<RefClass> mutualDependents = new HashSet<RefClass>(dependencies);
        mutualDependents.retainAll(dependents);

        final int numMutualDependents = mutualDependents.size();
        if (numMutualDependents <=1) {
            return null;
        }
        final String errorString =
                InspectionGadgetsBundle.message("cyclic.class.dependency.problem.descriptor", refEntity.getName(), numMutualDependents-1);

        return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
    }
}
