package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.ig.psiutils.CollectionUtils;

import javax.swing.*;

public class StaticCollectionInspection extends VariableInspection {
    
    /** @noinspection PublicField*/
    public boolean m_ignoreWeakCollections = false;

    public String getDisplayName() {
        return "Static collection";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Static collection #ref #loc";
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Ignore weak static collections or maps",
                                              this, "m_ignoreWeakCollections");
    }
    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StaticCollectionVisitor(this, inspectionManager, onTheFly);
    }

    private class StaticCollectionVisitor extends BaseInspectionVisitor {
        private StaticCollectionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(PsiField field) {
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final PsiType type = field.getType();
            if (type == null) {
                return;
            }
            if (!CollectionUtils.isCollectionClassOrInterface(type)) {
                return;
            }
            if(m_ignoreWeakCollections &&
                    !CollectionUtils.isWeakCollectionClass(type))
            registerFieldError(field);
        }

    }

}
