package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

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

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiIdentifier classNameIdentifier = (PsiIdentifier) descriptor.getPsiElement();
            final PsiClass interfaceClass = (PsiClass) classNameIdentifier.getParent();
                moveSubClassExtendsToImplements(interfaceClass);
                changeClassToInterface(interfaceClass);
                moveImplementsToExtends(interfaceClass);

        }

        private static void changeClassToInterface(PsiClass aClass)
                throws IncorrectOperationException {
            final PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
            assert nameIdentifier !=null;
            final PsiKeyword classKeyword = PsiTreeUtil.getPrevSiblingOfType(nameIdentifier, PsiKeyword.class);
            final PsiManager manager = aClass.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiKeyword interfaceKeyword = factory.createKeyword("interface");
            assert classKeyword != null;
            classKeyword.replace(interfaceKeyword);
        }

        private static void moveImplementsToExtends(PsiClass anInterface)
                throws IncorrectOperationException {
            final PsiReferenceList extendsList = anInterface.getExtendsList();
            final PsiReferenceList implementsList = anInterface.getImplementsList();
            assert implementsList != null;
            final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
            for(final PsiJavaCodeReferenceElement referenceElement : referenceElements){
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
            for(final PsiClass inheritor : inheritors){
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
            for(final PsiJavaCodeReferenceElement implementsReference : implementsReferences){
                final String implementsReferenceFqName = implementsReference.getQualifiedName();
                if(fqName.equals(implementsReferenceFqName)){
                    implementsReference.delete();
                }
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassMayBeInterfaceVisitor();
    }

    private static class ClassMayBeInterfaceVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if(aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()){
                return;
            }
            if(aClass instanceof PsiTypeParameter || aClass instanceof PsiAnonymousClass){
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

            return allFieldsPublicStaticFinal(aClass);
        }

        private static boolean allFieldsPublicStaticFinal(PsiClass aClass) {
            boolean allFieldsStaticFinal = true;
            final PsiField[] fields = aClass.getFields();
            for(final PsiField field : fields){
                if(!(field.hasModifierProperty(PsiModifier.STATIC)
                        && field.hasModifierProperty(PsiModifier.FINAL)
                        && field.hasModifierProperty(PsiModifier.PUBLIC))){
                    allFieldsStaticFinal = false;
                }
            }
            return allFieldsStaticFinal;
        }

        private static boolean allMethodsPublicAbstract(PsiClass aClass) {
            final PsiMethod[] methods = aClass.getMethods();
            for(final PsiMethod method : methods){
                if(!(method.hasModifierProperty(PsiModifier.ABSTRACT) &&
                        method.hasModifierProperty(PsiModifier.PUBLIC))){
                    return false;
                }
            }
            return true;
        }
    }

}
