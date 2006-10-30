package com.siyeh.ig.modularization;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ModuleWithTooFewClassesInspection extends BaseGlobalInspection {
    @SuppressWarnings({"PublicField"})
    public int limit = 10;

    public String getGroupDisplayName() {
        return GroupNames.MODULARIZATION_GROUP_NAME;
    }

    @Nullable
    public CommonProblemDescriptor[] checkElement(RefEntity refEntity,
                                                  AnalysisScope analysisScope,
                                                  InspectionManager inspectionManager,
                                                  GlobalInspectionContext globalInspectionContext) {
        if (!(refEntity instanceof RefModule)) {
            return null;
        }
        final RefModule refModule = (RefModule) refEntity;
        int numClasses = 0;
        final List<RefEntity> children = refModule.getChildren();
        for (RefEntity child : children) {
            if(child instanceof RefClass)
            {
                numClasses++;
            }
        }
        if(numClasses>=limit || numClasses ==0)
        {
            return null;
        }
        final String errorString =
                InspectionGadgetsBundle.message("module.with.too.few.classes.problem.descriptor", refModule.getName(), numClasses, limit);

        return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};

    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel(
                InspectionGadgetsBundle.message(
                        "module.with.too.few.classes.max.option"),
                this, "limit");
    }

    public boolean isGraphNeeded() {
        return false;
    }
}
