package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;

public class ClassMayBeInterfaceInspection extends ClassInspection {
    private final ClassMayBeInterfaceFix fix = new ClassMayBeInterfaceFix();

    public String getDisplayName() {
        return "Class may be interface";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref may be interface #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ClassMayBeInterfaceFix extends InspectionGadgetsFix {
        public String getName() {
            return "Convert class to interface";
        }

        public void applyFix(Project project, ProblemDescriptor problemDescriptor) {
            final PsiIdentifier classNameIdentifier = (PsiIdentifier) problemDescriptor.getPsiElement();
            final PsiClass interfaceClass = (PsiClass) classNameIdentifier.getParent();
            try {
                moveSubClassExtendsToImplements(interfaceClass);
                changeClassToInterface(interfaceClass);
                moveImplementsToExtends(interfaceClass);
            } catch (IncorrectOperationException e) {
                final Class aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }

        private static void changeClassToInterface(PsiClass aClass)
                throws IncorrectOperationException {
            final PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
            final PsiKeyword classKeyword = (PsiKeyword)PsiTreeUtil.getPrevSiblingOfType(nameIdentifier, PsiKeyword.class); 
            final PsiManager manager = aClass.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiKeyword interfaceKeyword = factory.createKeyword("interface");
            classKeyword.replace(interfaceKeyword);
        }

        private static void moveImplementsToExtends(PsiClass anInterface)
                throws IncorrectOperationException {
            final PsiReferenceList extendsList = anInterface.getExtendsList();
            final PsiReferenceList implementsList = anInterface.getImplementsList();
            final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
            for (int i = 0; i < referenceElements.length; i++) {
                final PsiJavaCodeReferenceElement referenceElement = referenceElements[i];
                final PsiElement elementCopy = referenceElement.copy();
                extendsList.add(elementCopy);
                referenceElement.delete();
            }
        }

        private static void moveSubClassExtendsToImplements(PsiClass oldClass)
                throws IncorrectOperationException {
            final PsiManager psiManager = oldClass.getManager();
            final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
            final PsiElementFactory elementFactory = psiManager.getElementFactory();
            final PsiJavaCodeReferenceElement classReference = elementFactory.createClassReferenceElement(oldClass);
            final SearchScope searchScope = oldClass.getUseScope();
            final PsiClass[] inheritors = searchHelper.findInheritors(oldClass, searchScope, false);
            for (int i = 0; i < inheritors.length; i++) {
                final PsiClass inheritor = inheritors[i];
                final PsiReferenceList extendsList = inheritor.getExtendsList();
                removeReference(extendsList, classReference);
                final PsiReferenceList implementsList = inheritor.getImplementsList();
                implementsList.add(classReference);
            }
        }

        private static void removeReference(PsiReferenceList referenceList,
                                            PsiJavaCodeReferenceElement reference)
                throws IncorrectOperationException {
            final PsiJavaCodeReferenceElement[] implementsReferences = referenceList.getReferenceElements();
            final String fqName = reference.getQualifiedName();
            for (int j = 0; j < implementsReferences.length; j++) {
                final PsiJavaCodeReferenceElement implementsReference = implementsReferences[j];
                final String implementsReferenceFqName = implementsReference.getQualifiedName();
                if (fqName.equals(implementsReferenceFqName)) {
                    implementsReference.delete();
                }
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassMayBeInterfaceVisitor(this, inspectionManager, onTheFly);
    }

    private static class ClassMayBeInterfaceVisitor extends BaseInspectionVisitor {
        private ClassMayBeInterfaceVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (aClass.isEnum() || aClass.isAnnotationType()) {
                return;
            }
            if (!mayBeInterface(aClass)) {
                return;
            }
            registerClassError(aClass);
        }

        public static boolean mayBeInterface(PsiClass aClass) {
            final PsiReferenceList extendsList = aClass.getExtendsList();

            if (extendsList != null) {
                final PsiJavaCodeReferenceElement[] extendsElements = extendsList.getReferenceElements();
                if (extendsElements != null && extendsElements.length > 0) {
                    return false;
                }
            }
            final PsiClassInitializer[] initializers = aClass.getInitializers();
            if (initializers != null && initializers.length > 0) {
                return false;
            }
            if (!allMethodsPublicAbstract(aClass)) {
                return false;
            }

            if (!allFieldsPublicStaticFinal(aClass)) {
                return false;
            }
            return true;
        }

        private static boolean allFieldsPublicStaticFinal(PsiClass aClass) {
            boolean allFieldsStaticFinal = true;
            final PsiField[] fields = aClass.getFields();
            for (int i = 0; i < fields.length; i++) {
                final PsiField field = fields[i];
                if (!(field.hasModifierProperty(PsiModifier.STATIC)
                        && field.hasModifierProperty(PsiModifier.FINAL)
                        && field.hasModifierProperty(PsiModifier.PUBLIC))) {
                    allFieldsStaticFinal = false;
                }
            }
            return allFieldsStaticFinal;
        }

        private static boolean allMethodsPublicAbstract(PsiClass aClass) {
            final PsiMethod[] methods = aClass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                final PsiMethod method = methods[i];
                if (!(method.hasModifierProperty(PsiModifier.ABSTRACT) &&
                        method.hasModifierProperty(PsiModifier.PUBLIC))) {
                    return false;
                }
            }
            return true;
        }
    }

}
