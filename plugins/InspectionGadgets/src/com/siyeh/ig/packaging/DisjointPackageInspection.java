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
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.dependency.DependencyUtils;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class DisjointPackageInspection extends BaseGlobalInspection {

    public String getGroupDisplayName() {
        return GroupNames.PACKAGING_GROUP_NAME;
    }

    @Nullable
    public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope analysisScope, InspectionManager inspectionManager, GlobalInspectionContext globalInspectionContext) {
        if (!(refEntity instanceof RefPackage)) {
            return null;
        }
        if (globalInspectionContext.isSuppressed(refEntity, getShortName())) {
            return null;
        }
        final RefPackage refPackage = (RefPackage) refEntity;
        final List<RefEntity> children = refPackage.getChildren();
        final Set<RefClass> childClasses = new HashSet<RefClass>();
        for (RefEntity child : children) {
            if (!(child instanceof RefClass)) {
                continue;
            }
            final PsiClass psiClass = ((RefClass) child).getElement();
            if(ClassUtils.isInnerClass(psiClass))
            {
                continue;
            }
            childClasses.add((RefClass) child);
        }
        if (childClasses.size() == 0) {
            return null;
        }
        final Set<Set<RefClass>> components = createComponents(refPackage, childClasses);
        if (components.size() == 1) {
            return null;
        }
        final String errorString = InspectionGadgetsBundle
                .message("disjoint.package.problem.descriptor", refPackage.getQualifiedName(), components.size());

        return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
    }

    private static Set<Set<RefClass>> createComponents(RefPackage aPackage, Set<RefClass> classes) {
        final Set<RefClass> allClasses = new HashSet<RefClass>(classes);
        final Set<Set<RefClass>> out = new HashSet<Set<RefClass>>();
        while (allClasses.size() > 0) {
            final Set<RefClass> currentComponent = new HashSet<RefClass>();
            final List<RefClass> pendingClasses = new ArrayList<RefClass>();
            final RefClass seed = allClasses.iterator().next();
            allClasses.remove(seed);
            currentComponent.add(seed);
            pendingClasses.add(seed);
            while (pendingClasses.size() > 0) {
                final RefClass classToProcess = pendingClasses.remove(0);
                final Set<RefClass> relatedClasses = getRelatedClasses(aPackage, classToProcess);
                for (RefClass relatedClass : relatedClasses) {
                    if (!currentComponent.contains(relatedClass) &&
                            !pendingClasses.contains(relatedClass)) {
                        currentComponent.add(relatedClass);
                        pendingClasses.add(relatedClass);
                        allClasses.remove(relatedClass);
                    }
                }
            }
            out.add(currentComponent);
        }
        return out;
    }

    private static Set<RefClass> getRelatedClasses(RefPackage aPackage, RefClass classToProcess) {
        final Set<RefClass> out = new HashSet<RefClass>();
        final Set<RefClass> dependencies =
                DependencyUtils.calculateDependenciesForClass(classToProcess);
        for (RefClass dependency : dependencies) {
            if (packageContainsClass(aPackage, dependency)) {
                out.add(dependency);
            }
        }

        final Set<RefClass> dependents = DependencyUtils.calculateDependentsForClass(classToProcess);
        for (RefClass dependent : dependents) {
            if (packageContainsClass(aPackage, dependent)) {
                out.add(dependent);
            }
        }
        return out;
    }

    private static boolean packageContainsClass(RefPackage aPackage, RefClass aClass) {
        final RefUtil refUtil = RefUtil.getInstance();
        final RefPackage packageForClass = refUtil.getPackage(aClass);
        return aPackage.equals(packageForClass);
    }
}
