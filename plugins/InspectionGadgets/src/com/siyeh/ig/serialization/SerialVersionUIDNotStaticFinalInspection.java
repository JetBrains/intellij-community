package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.SerializationUtils;

public class SerialVersionUIDNotStaticFinalInspection extends ClassInspection {
    private static final Logger s_logger = Logger.getInstance("SerialVersionUIDNotStaticFinalInspection");
    private final MakeStaticFinalFix fix = new MakeStaticFinalFix();

    public String getDisplayName() {
        return "'serialVersionUID' field not declared 'static final'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref field of a Serializable class is not declared 'static' and 'final' #loc ";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SerializableDefinesSerialVersionUIDVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class MakeStaticFinalFix extends InspectionGadgetsFix {
        public String getName() {
            return "Make 'static final'";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            try {
                final PsiElement fieldNameToken = descriptor.getPsiElement();
                final PsiField field = (PsiField) fieldNameToken.getParent();
                final PsiModifierList modifiers = field.getModifierList();
                if (!modifiers.hasModifierProperty(PsiModifier.STATIC)) {
                    modifiers.setModifierProperty(PsiModifier.STATIC, true);
                }
                if (!modifiers.hasModifierProperty(PsiModifier.FINAL)) {
                    modifiers.setModifierProperty(PsiModifier.FINAL, true);
                }
            } catch (IncorrectOperationException e) {
                s_logger.error(e);
            }
        }
    }

    private static class SerializableDefinesSerialVersionUIDVisitor extends BaseInspectionVisitor {
        private SerializableDefinesSerialVersionUIDVisitor(BaseInspection inspection,
                                                           InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            final PsiField[] fields = aClass.getFields();
            for (int i = 0; i < fields.length; i++) {
                final PsiField field = fields[i];
                if (isSerialVersionUID(field)) {
                    if (!field.hasModifierProperty(PsiModifier.STATIC) ||
                            !field.hasModifierProperty(PsiModifier.FINAL)) {
                        registerFieldError(field);
                    }
                }
            }
        }

        private static boolean isSerialVersionUID(PsiField field) {
            final String fieldName = field.getName();
            return "serialVersionUID".equals(fieldName);
        }

    }

}
