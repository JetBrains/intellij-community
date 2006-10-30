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
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public class ClassWithTooManyDependentsInspection extends BaseGlobalInspection {
    @SuppressWarnings({"PublicField"})
    public int limit = 10;

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
        if (ClassUtils.isInnerClass(aClass)) {
            return null;
        }

        final Set<RefClass> dependents = DependencyUtils.calculateDependentsForClass(refClass);
        final int numDependents = dependents.size();
        if (numDependents <= limit) {
            return null;
        }
        final String errorString =
                InspectionGadgetsBundle.message("class.with.too.many.dependents.problem.descriptor", refEntity.getName(), numDependents, limit);

        return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};

    }


    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel(
                InspectionGadgetsBundle.message("class.with.too.many.dependents.max.option"),
                this, "limit");
    }
}
