package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;

public class FieldMayBeStaticInspection extends FieldInspection{
    private static final Logger s_logger =
            Logger.getInstance("FieldMayBeStaticInspection");
    private final MakeStaticFix fix = new MakeStaticFix();

    public String getDisplayName(){
        return "Field may be 'static'";
    }

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Field #ref may be 'static' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new FieldMayBeStaticVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class MakeStaticFix extends InspectionGadgetsFix{
        public String getName(){
            return "Make static";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(descriptor)){
                return;
            }
            final PsiJavaToken m_fieldNameToken =
                    (PsiJavaToken) descriptor.getPsiElement();
            try{
                final PsiField field = (PsiField) m_fieldNameToken.getParent();
                final PsiModifierList modifiers = field.getModifierList();
                modifiers.setModifierProperty(PsiModifier.STATIC, true);
            } catch(IncorrectOperationException e){
                s_logger.error(e);
            }
        }
    }

    private static class FieldMayBeStaticVisitor extends BaseInspectionVisitor{
        private FieldMayBeStaticVisitor(BaseInspection inspection,
                                        InspectionManager inspectionManager,
                                        boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(@NotNull PsiField field){
            if(field.hasModifierProperty(PsiModifier.STATIC)){
                return;
            }
            if(!field.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            final PsiExpression initializer = field.getInitializer();
            if(initializer == null){
                return;
            }
            if(SideEffectChecker.mayHaveSideEffects(initializer)){
                return;
            }
            if(!canBeStatic(initializer)){
                return;
            }
            final PsiType type = field.getType();
            if(type == null){
                return;
            }

            if(!ClassUtils.isImmutable(type)){
                return;
            }
            registerFieldError(field);
        }

        private static boolean canBeStatic(PsiExpression initializer){
            final CanBeStaticVisitor canBeStaticVisitor =
                    new CanBeStaticVisitor();
            initializer.accept(canBeStaticVisitor);
            return canBeStaticVisitor.canBeStatic();
        }
    }
}
