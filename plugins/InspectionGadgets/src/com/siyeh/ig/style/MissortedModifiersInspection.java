package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;

import java.util.*;

public class MissortedModifiersInspection extends ClassInspection {
    private static final Logger s_logger = Logger.getInstance("MissortedModifiersInspection");

    private static final int NUM_MODIFIERS = 11;
    private static final Map s_modifierOrder = new HashMap(NUM_MODIFIERS);
    private final SortModifiersFix fix = new SortModifiersFix();

    static {
        s_modifierOrder.put("public", new Integer(0));
        s_modifierOrder.put("protected", new Integer(1));
        s_modifierOrder.put("private", new Integer(2));
        s_modifierOrder.put("static", new Integer(3));
        s_modifierOrder.put("abstract", new Integer(4));
        s_modifierOrder.put("final", new Integer(5));
        s_modifierOrder.put("transient", new Integer(6));
        s_modifierOrder.put("volatile", new Integer(7));
        s_modifierOrder.put("synchronized", new Integer(8));
        s_modifierOrder.put("native", new Integer(9));
        s_modifierOrder.put("strictfp", new Integer(10));
    }

    public String getDisplayName() {
        return "Missorted modifers";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Missorted modifers '#ref' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MissortedModifiersVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class SortModifiersFix extends InspectionGadgetsFix {
        public String getName() {
            return "Sort modifers";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiModifierList modifierList = (PsiModifierList) descriptor.getPsiElement();
            final List simpleModifiers = new ArrayList();
            final PsiElement[] children = modifierList.getChildren();
            for (int i = 0; i < children.length; i++) {
                final PsiElement child = children[i];

                if (child instanceof PsiJavaToken) {
                    simpleModifiers.add(child.getText());
                }
                if (child instanceof PsiAnnotation) {
                }
            }
            Collections.sort(simpleModifiers, new ModifierComparator());
            clearModifiers(simpleModifiers, modifierList);
            addModifiersInOrder(simpleModifiers, modifierList);

        }

        private static void addModifiersInOrder(List modifiers,
                                                PsiModifierList modifierList) {
            for (Iterator iterator = modifiers.iterator(); iterator.hasNext();) {
                final String modifier = (String) iterator.next();
                try {
                    modifierList.setModifierProperty(modifier, true);
                } catch (IncorrectOperationException e) {
                    s_logger.error(e);
                }
            }
        }

        private static void clearModifiers(List modifiers,
                                           PsiModifierList modifierList) {
            for (Iterator iterator = modifiers.iterator(); iterator.hasNext();) {
                final String modifier = (String) iterator.next();
                try {
                    modifierList.setModifierProperty(modifier, false);
                } catch (IncorrectOperationException e) {
                    s_logger.error(e);
                }
            }
        }
    }

    private static class MissortedModifiersVisitor
            extends BaseInspectionVisitor {
        private boolean m_isInClass = false;

        private MissortedModifiersVisitor(BaseInspection inspection,
                                          InspectionManager inspectionManager,
                                          boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            if (!m_isInClass) {
                m_isInClass = true;
                super.visitClass(aClass);
                checkForMissortedModifiers(aClass);
                m_isInClass = false;
            }
        }

        public void visitClassInitializer(PsiClassInitializer initializer) {
            super.visitClassInitializer(initializer);
            checkForMissortedModifiers(initializer);
        }

        public void visitLocalVariable(PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            checkForMissortedModifiers(variable);
        }

        public void visitParameter(PsiParameter parameter) {
            super.visitParameter(parameter);
            checkForMissortedModifiers(parameter);
        }

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            checkForMissortedModifiers(method);
        }

        public void visitField(PsiField field) {
            super.visitField(field);
            checkForMissortedModifiers(field);
        }

        private void checkForMissortedModifiers(PsiModifierListOwner listOwner) {
            final PsiModifierList modifierList = listOwner.getModifierList();
            if (isModifierListMissorted(modifierList)) {
                registerError(modifierList);
            }
        }

        private static boolean isModifierListMissorted(PsiModifierList modifierList) {
            if (modifierList == null) {
                return false;
            }

            final List simpleModifiers = new ArrayList();
            final PsiElement[] children = modifierList.getChildren();
            for (int i = 0; i < children.length; i++) {
                final PsiElement child = children[i];

                if (child instanceof PsiJavaToken) {
                    simpleModifiers.add(child);
                }
                if (child instanceof PsiAnnotation) {
                    if (simpleModifiers.size() != 0) {
                        return true; //things aren't in order, since annotations come first
                    }
                }
            }
            int currentModifierIndex = -1;

            for (Iterator iterator = simpleModifiers.iterator();
                 iterator.hasNext();) {
                final PsiJavaToken token = (PsiJavaToken) iterator.next();
                final String text = token.getText();
                final Integer modifierIndex = (Integer) s_modifierOrder.get(text);
                if (modifierIndex == null) {
                    return false;
                }
                final int nextModifierIndex = modifierIndex.intValue();
                if (currentModifierIndex >= nextModifierIndex) {
                    return true;
                }
                currentModifierIndex = nextModifierIndex;
            }
            return false;
        }

    }

}
