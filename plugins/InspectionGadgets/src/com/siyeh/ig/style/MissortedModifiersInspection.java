package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class MissortedModifiersInspection extends ClassInspection {

    private static final int NUM_MODIFIERS = 11;
    /** @noinspection StaticCollection*/
    private static final Map<String,Integer> s_modifierOrder = new HashMap<String, Integer>(NUM_MODIFIERS);

    public boolean m_requireAnnotationsFirst = true;

    private final SortModifiersFix fix = new SortModifiersFix();

    static {
        s_modifierOrder.put("public", 0);
        s_modifierOrder.put("protected", 1);
        s_modifierOrder.put("private", 2);
        s_modifierOrder.put("static", 3);
        s_modifierOrder.put("abstract", 4);
        s_modifierOrder.put("final", 5);
        s_modifierOrder.put("transient", 6);
        s_modifierOrder.put("volatile", 7);
        s_modifierOrder.put("synchronized", 8);
        s_modifierOrder.put("native", 9);
        s_modifierOrder.put("strictfp", 10);
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

    public BaseInspectionVisitor buildVisitor() {
        return new MissortedModifiersVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }
         public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Require annotations to be sorted before keywords",
                                                              this, "m_requireAnnotationsFirst");
    }
    private static class SortModifiersFix extends InspectionGadgetsFix {
        public String getName() {
            return "Sort modifers";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{

            final PsiModifierList modifierList = (PsiModifierList) descriptor.getPsiElement();
            final List<String> simpleModifiers = new ArrayList<String>();
            final PsiElement[] children = modifierList.getChildren();
            for(final PsiElement child : children){
                if(child instanceof PsiJavaToken){
                    simpleModifiers.add(child.getText());
                }
                if(child instanceof PsiAnnotation){
                }
            }
            Collections.sort(simpleModifiers, new ModifierComparator());
            clearModifiers(simpleModifiers, modifierList);
            addModifiersInOrder(simpleModifiers, modifierList);
        }

        private static void addModifiersInOrder(List<String> modifiers,
                                                PsiModifierList modifierList)
                throws IncorrectOperationException{
            for(String modifier : modifiers){
                    modifierList.setModifierProperty(modifier, true);
            }
        }

        private static void clearModifiers(List<String> modifiers,
                                           PsiModifierList modifierList)
                                                                         throws IncorrectOperationException{
            for(final String modifier : modifiers){
                    modifierList.setModifierProperty(modifier, false);

            }
        }
    }

    private class MissortedModifiersVisitor
            extends BaseInspectionVisitor {
        private boolean m_isInClass = false;

        public void visitClass(@NotNull PsiClass aClass) {
            if (!m_isInClass) {
                m_isInClass = true;
                super.visitClass(aClass);
                checkForMissortedModifiers(aClass);
                m_isInClass = false;
            }
        }

        public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
            super.visitClassInitializer(initializer);
            checkForMissortedModifiers(initializer);
        }

        public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            checkForMissortedModifiers(variable);
        }

        public void visitParameter(@NotNull PsiParameter parameter) {
            super.visitParameter(parameter);
            checkForMissortedModifiers(parameter);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            checkForMissortedModifiers(method);
        }

        public void visitField(@NotNull PsiField field) {
            super.visitField(field);
            checkForMissortedModifiers(field);
        }

        private void checkForMissortedModifiers(PsiModifierListOwner listOwner) {
            final PsiModifierList modifierList = listOwner.getModifierList();
            if (isModifierListMissorted(modifierList)) {
                registerError(modifierList);
            }
        }

        private boolean isModifierListMissorted(PsiModifierList modifierList) {
            if (modifierList == null) {
                return false;
            }

            final List<PsiElement> simpleModifiers = new ArrayList<PsiElement>();
            final PsiElement[] children = modifierList.getChildren();
            for(final PsiElement child : children){
                if(child instanceof PsiJavaToken){
                    simpleModifiers.add(child);
                }
                if(child instanceof PsiAnnotation){
                    if(m_requireAnnotationsFirst && simpleModifiers.size() != 0){
                        return true; //things aren't in order, since annotations come first
                    }
                }
            }
            int currentModifierIndex = -1;

            for(Object simpleModifier : simpleModifiers){
                final PsiJavaToken token = (PsiJavaToken) simpleModifier;
                final String text = token.getText();
                final Integer modifierIndex = s_modifierOrder.get(text);
                if(modifierIndex == null){
                    return false;
                }
                if(currentModifierIndex >= modifierIndex){
                    return true;
                }
                currentModifierIndex = modifierIndex;
            }
            return false;
        }

    }

}
