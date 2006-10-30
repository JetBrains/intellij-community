package com.siyeh.ig.global;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class StaticFieldCanBeMovedToUseInspection extends BaseGlobalInspection {

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    @Nullable
    public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope analysisScope, InspectionManager inspectionManager, GlobalInspectionContext globalInspectionContext) {
        if (!(refEntity instanceof RefField)) {
            return null;
        }
        final RefField refField = (RefField) refEntity;
        final PsiField field = refField.getElement();
        if (field == null) {
            return null;
        }
        final PsiType type = field.getType();
        if (!type.equals(PsiType.BOOLEAN)) {
            return null;
        }

        final RefClass fieldClass = refField.getOwnerClass();
        final Collection<RefElement> inReferences = refField.getInReferences();
        final RefUtil refUtil = RefUtil.getInstance();
        final Set<RefClass> classesUsed = new HashSet<RefClass>();
        for (RefElement inReference : inReferences) {
            final RefClass referringClass = refUtil.getOwnerClass(inReference);
            if (referringClass == null) {
                return null;
            }
            if (referringClass.equals(fieldClass)) {
                return null;
            }
            classesUsed.add(referringClass);
            if (classesUsed.size() > 1) {
                return null;
            }
        }
        if (classesUsed.size() != 1) {
            return null;
        }
        final RefClass referencingClass = classesUsed.iterator().next();
        final String errorString = "Static field " + refEntity.getName() + " is only accessed in subclass " + referencingClass.getName();
        return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
    }
}
