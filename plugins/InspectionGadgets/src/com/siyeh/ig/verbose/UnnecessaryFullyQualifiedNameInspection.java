package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class UnnecessaryFullyQualifiedNameInspection extends ClassInspection{
    public boolean m_ignoreJavadoc = false;

    private final UnnecessaryFullyQualifiedNameFix fix =
            new UnnecessaryFullyQualifiedNameFix();

    public String getDisplayName(){
        return "Unnecessary fully qualified name";
    }

    public String getGroupDisplayName(){
        return com.siyeh.ig.GroupNames.VERBOSE_GROUP_NAME;
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Ignore fully qualified names in javadoc",
                                              this,
                                              "m_ignoreJavadoc");
    }

    public String buildErrorString(PsiElement location){
        return "Fully qualified name #ref is unnecessary, and can be replaced with an import #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new UnnecessaryFullyQualifiedNameVisitor(this, inspectionManager,
                                                        onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class UnnecessaryFullyQualifiedNameFix
            extends InspectionGadgetsFix{
        public String getName(){
            return "Replace with import";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final CodeStyleSettingsManager settingsManager =
                    CodeStyleSettingsManager.getInstance(project);
            final CodeStyleSettings settings =
                    settingsManager.getCurrentSettings();
            final boolean oldUseFQNamesInJavadoc =
                    settings.USE_FQ_CLASS_NAMES_IN_JAVADOC;
            final boolean oldUseFQNames = settings.USE_FQ_CLASS_NAMES;
            try{
                settings.USE_FQ_CLASS_NAMES_IN_JAVADOC = false;
                settings.USE_FQ_CLASS_NAMES = false;
                final PsiJavaCodeReferenceElement reference =
                        (PsiJavaCodeReferenceElement) descriptor.getPsiElement();
                final PsiManager psiManager = reference.getManager();
                final CodeStyleManager styleManager =
                        psiManager.getCodeStyleManager();
                styleManager.shortenClassReferences(reference);
            } catch(IncorrectOperationException e){
                final Class thisClass = getClass();
                final String className = thisClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            } finally{
                settings.USE_FQ_CLASS_NAMES_IN_JAVADOC = oldUseFQNamesInJavadoc;
                settings.USE_FQ_CLASS_NAMES = oldUseFQNames;
            }
        }
    }

    private class UnnecessaryFullyQualifiedNameVisitor
            extends BaseInspectionVisitor{
        private boolean m_inClass = false;

        private UnnecessaryFullyQualifiedNameVisitor(BaseInspection inspection,
                                                     InspectionManager inspectionManager,
                                                     boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass){
            final boolean wasInClass = m_inClass;
            if(!m_inClass){

                m_inClass = true;
                super.visitClass(aClass);
            }
            m_inClass = wasInClass;
        }

        public void visitReferenceElement(PsiJavaCodeReferenceElement element){
            if(m_ignoreJavadoc)
            {
                final PsiElement containingComment =
                        PsiTreeUtil.getParentOfType(element, PsiDocComment.class);
                if(containingComment!=null)
                {
                    return;
                }
            }
            final String text = element.getText();
            if(text.indexOf((int) '.') < 0){
                return;
            }
            final PsiElement psiElement = element.resolve();
            if(!(psiElement instanceof PsiClass)){
                return;
            }
            final PsiReferenceParameterList typeParameters =
                    element.getParameterList();
            if(typeParameters != null){
                typeParameters.accept(this);
            }
            final PsiClass aClass = (PsiClass) psiElement;
            final PsiClass outerClass =
                    ClassUtils.getOutermostContainingClass(aClass);
            final String fqName = outerClass.getQualifiedName();
            if(!text.startsWith(fqName)){
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) element.getContainingFile();
            if(!ImportUtils.nameCanBeImported(text, file)){
                return;
            }
            registerError(element);
        }
    }
}
