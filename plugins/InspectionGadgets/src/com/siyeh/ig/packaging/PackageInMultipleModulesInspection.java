package com.siyeh.ig.packaging;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.reference.RefPackage;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PackageInMultipleModulesInspection extends BaseGlobalInspection {


    public String getGroupDisplayName() {
        return GroupNames.PACKAGING_GROUP_NAME;
    }

    @Nullable
    public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope analysisScope, InspectionManager inspectionManager, GlobalInspectionContext globalInspectionContext) {
        if (!(refEntity instanceof RefPackage)) {
            return null;
        }
        final Set<RefModule> modules = new HashSet<RefModule>();
        final RefPackage refPackage = (RefPackage) refEntity;
        final List<RefEntity> children = refPackage.getChildren();
        for (RefEntity child : children) {
            if (child instanceof RefClass) {
                final RefClass refClass = (RefClass) child;
                final RefModule module = refClass.getModule();
                modules.add(module);
            }
        }
        if (modules.size() <= 1) {
            return null;
        }
        final String errorString =
                InspectionGadgetsBundle.message("package.in.multiple.modules.problem.descriptor", refPackage.getQualifiedName());

        return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};

    }

    public boolean isGraphNeeded() {
        return false;
    }
}
