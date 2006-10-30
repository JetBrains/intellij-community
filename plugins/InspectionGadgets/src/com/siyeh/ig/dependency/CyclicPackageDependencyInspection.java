package com.siyeh.ig.dependency;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class CyclicPackageDependencyInspection extends BaseGlobalInspection {

    public String getGroupDisplayName() {
        return GroupNames.DEPENDENCY_GROUP_NAME;
    }

    @Nullable
    public CommonProblemDescriptor[] checkElement(RefEntity refEntity,
                                                  AnalysisScope analysisScope,
                                                  InspectionManager inspectionManager,
                                                  GlobalInspectionContext globalInspectionContext) {
        if (!(refEntity instanceof RefPackage)) {
            return null;
        }
        final RefPackage refPackage = (RefPackage) refEntity;

        final Set<RefPackage> dependencies =
                DependencyUtils.calculateTransitiveDependenciesForPackage(refPackage);
        final Set<RefPackage> dependents =
                DependencyUtils.calculateTransitiveDependentsForPackage(refPackage);
        final Set<RefPackage> mutualDependents = new HashSet<RefPackage>(dependencies);
        mutualDependents.retainAll(dependents);

        final int numMutualDependents = mutualDependents.size();
        if (numMutualDependents <=1) {
            return null;
        }
        final String packageName = refEntity.getName();
        final String errorString =
                InspectionGadgetsBundle.message("cyclic.package.dependency.problem.descriptor", packageName, numMutualDependents-1);

        return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};

    }
}
