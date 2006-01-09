/**
 * (c) 2004 Carp Technologies BV
 * Hengelosestraat 705, 7521PA Enschede
 * Created: Jan 8, 2006, 6:22:31 PM
 */
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.InheritanceUtil;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class InterfaceNeverImplementedInspection extends ClassInspection {

    /** @noinspection PublicField*/
    public boolean ignoreInterfacesThatOnlyDeclareConstants = false;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "interface.never.implemented.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.INHERITANCE_GROUP_NAME;
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "interface.never.implemented.option"), this,
                "ignoreInterfacesThatOnlyDeclareConstants");
    }

    @Nullable
    protected String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message(
                "interface.never.implemented.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InterfaceNeverImplementedVisitor();
    }

    private class InterfaceNeverImplementedVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            if (!aClass.isInterface()) {
                return;
            }
            if (ignoreInterfacesThatOnlyDeclareConstants &&
                    aClass.getMethods().length == 0) {
                if (aClass.getFields().length != 0) {
                    return;
                }
            }
            if (InheritanceUtil.hasImplementation(aClass)) {
                return;
            }
            registerClassError(aClass);
        }
    }
}