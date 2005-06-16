package com.siyeh.ig.serialization;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeParameter;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SerializableHasSerializationMethodsInspection extends ClassInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreSerializableDueToInheritance = true;

    public String getDisplayName() {
        return "Serializable class without 'readObject()' and 'writeObject()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        assert aClass != null;
        final boolean hasReadObject = SerializationUtils.hasReadObject(aClass);
        final boolean hasWriteObject = SerializationUtils.hasWriteObject(aClass);

        if (!hasReadObject && !hasWriteObject) {
            return "#ref doesn't define readObject() or writeObject() #loc";
        } else if (hasReadObject) {
            return "#ref doesn't define writeObject() #loc";
        } else {
            return "#ref doesn't define readObject() #loc";
        }
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore classes serializable due to inheritance",
                this, "m_ignoreSerializableDueToInheritance");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SerializableDefinesMethodsVisitor();
    }

    private class SerializableDefinesMethodsVisitor extends BaseInspectionVisitor {


        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType() ||
                        aClass.isEnum()) {
                return;
            }
            if(aClass instanceof PsiTypeParameter ||
                    aClass instanceof PsiAnonymousClass){
                return;
            }
            if (m_ignoreSerializableDueToInheritance) {
                if (!SerializationUtils.isDirectlySerializable(aClass)) {
                    return;
                }
            } else {
                if (!SerializationUtils.isSerializable(aClass)) {
                    return;
                }
            }
            final boolean hasReadObject = SerializationUtils.hasReadObject(aClass);
            final boolean hasWriteObject = SerializationUtils.hasWriteObject(aClass);

            if (hasWriteObject && hasReadObject) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
