package com.siyeh.ig.dependency;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;
import java.util.Set;

public class ClassWithTooManyDependenciesInspection extends BaseGlobalInspection {
    @SuppressWarnings({"PublicField"})
    public int limit = 10;

    public String getGroupDisplayName() {
        return GroupNames.DEPENDENCY_GROUP_NAME;
    }


    public void runInspection(AnalysisScope scope,
                              final InspectionManager inspectionManager,
                              GlobalInspectionContext globalInspectionContext,
                              final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
        final RefManager refManager = globalInspectionContext.getRefManager();
        refManager.iterate(new RefVisitor(){

            public void visitClass(RefClass refClass) {
                super.visitClass(refClass);
                final PsiClass aClass = refClass.getElement();
                if (ClassUtils.isInnerClass(aClass)) {
                    return;
                }
                final Set<RefClass> dependencies =
                        DependencyUtils.calculateDependenciesForClass(refClass);
                final int numDependencies = dependencies.size();
                if (numDependencies <= limit) {
                    return ;
                }
                final String errorString =
                        InspectionGadgetsBundle.message("class.with.too.many.dependencies.problem.descriptor", refClass.getName(), numDependencies, limit);
                final CommonProblemDescriptor[] descriptors = new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
                problemDescriptionsProcessor.addProblemElement(refClass, descriptors);
            }
        });  
    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel(
                InspectionGadgetsBundle.message(
                        "class.with.too.many.dependencies.max.option"),
                this, "limit");
    }
}
