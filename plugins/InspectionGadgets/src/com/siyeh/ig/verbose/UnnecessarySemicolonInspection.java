package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;

public class UnnecessarySemicolonInspection extends ClassInspection {
    private final UnnecessarySemicolonFix fix = new UnnecessarySemicolonFix();

    public String getDisplayName() {
        return "Unnecessary semicolon";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public String buildErrorString(PsiElement location) {
        return "Unnecessary semicolon #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessarySemicolonVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessarySemicolonFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unnecessary semicolon";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiElement semicolonElement = descriptor.getPsiElement();
            deleteElement(semicolonElement);
        }

    }

    private static class UnnecessarySemicolonVisitor extends BaseInspectionVisitor {
        private UnnecessarySemicolonVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            PsiElement sibling = skipForwardWhiteSpacesAndComments(aClass);
            while (sibling != null) {
                if (sibling instanceof PsiJavaToken &&
                        ((PsiJavaToken) sibling).getTokenType().equals(JavaTokenType.SEMICOLON)) {
                    registerError(sibling);
                } else {
                    break;
                }
                sibling = skipForwardWhiteSpacesAndComments(sibling);
            }

            //TODO: Dave, correct me if I'm wrong but I think that only semicolon after last member in enum is unneccessary
            //Also your indentation level differs from ours:)
            if (aClass.isEnum()) {
              final PsiField[] fields = aClass.getFields();
              if (fields.length > 0) {
                final PsiField last = fields[fields.length - 1];
                if (last instanceof PsiEnumConstant) {
                  PsiElement element = skipForwardWhiteSpacesAndComments(last);
                  if (element instanceof PsiJavaToken &&
                      ((PsiJavaToken)element).getTokenType().equals(JavaTokenType.SEMICOLON)) {
                    final PsiElement next = skipForwardWhiteSpacesAndComments(element);
                    if (next == null || next == aClass.getRBrace()) {
                      registerError(element);
                    }
                  }
                }
              }
            }
        }

      private PsiElement skipForwardWhiteSpacesAndComments(final PsiElement element) {
        return PsiTreeUtil.skipSiblingsForward(element, new Class[]{PsiWhiteSpace.class, PsiComment.class});
      }

      public void visitEmptyStatement(PsiEmptyStatement statement) {
            super.visitEmptyStatement(statement);
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiCodeBlock) {
                registerError(statement);
            }
        }
    }
}
