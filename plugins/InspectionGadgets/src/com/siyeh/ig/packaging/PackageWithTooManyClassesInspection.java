package com.siyeh.ig.packaging;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class PackageWithTooManyClassesInspection extends BaseGlobalInspection {
    @SuppressWarnings({"PublicField"})
    public int limit = 10;

    public String getGroupDisplayName() {
        return GroupNames.PACKAGING_GROUP_NAME;
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
        int numClasses = 0;
        final List<RefEntity> children = refPackage.getChildren();
        for (RefEntity child : children) {
            if(child instanceof RefClass)
            {
                numClasses++;
            }
        }
        if(numClasses<=limit)
        {
            return null;
        }
        final String errorString =
                InspectionGadgetsBundle.message("package.with.too.many.classes.problem.descriptor", refPackage.getQualifiedName(), numClasses, limit);

        return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};

    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel(
                InspectionGadgetsBundle.message(
                        "package.with.too.many.classes.max.option"),
                this, "limit");
    }

    public boolean isGraphNeeded() {
        return false;
    }
}
