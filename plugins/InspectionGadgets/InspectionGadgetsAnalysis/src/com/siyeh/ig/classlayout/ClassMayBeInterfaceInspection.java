/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ClassMayBeInterfaceInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("class.may.be.interface.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("class.may.be.interface.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ClassMayBeInterfaceFix();
  }

  private static class ClassMayBeInterfaceFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("class.may.be.interface.convert.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected boolean prepareForWriting() {
      return false;
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiIdentifier classNameIdentifier = (PsiIdentifier)descriptor.getPsiElement();
      final PsiClass interfaceClass = (PsiClass)classNameIdentifier.getParent();
      final SearchScope searchScope = interfaceClass.getUseScope();
      final List<PsiClass> elements = new ArrayList();
      elements.add(interfaceClass);
      for (final PsiClass inheritor : ClassInheritorsSearch.search(interfaceClass, searchScope, false)) {
        elements.add(inheritor);
      }
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) {
        return;
      }
      moveSubClassExtendsToImplements(elements);
      changeClassToInterface(interfaceClass);
      moveImplementsToExtends(interfaceClass);
    }

    private static void changeClassToInterface(PsiClass aClass) {
      final PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      final PsiKeyword classKeyword = PsiTreeUtil.getPrevSiblingOfType(nameIdentifier, PsiKeyword.class);
      final PsiManager manager = aClass.getManager();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      final PsiKeyword interfaceKeyword = factory.createKeyword(PsiKeyword.INTERFACE);
      if (classKeyword == null) {
        return;
      }
      final PsiModifierList modifierList = aClass.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
        modifierList.setModifierProperty(PsiModifier.FINAL, false);
      }
      classKeyword.replace(interfaceKeyword);
    }

    private static void moveImplementsToExtends(PsiClass anInterface) {
      final PsiReferenceList extendsList = anInterface.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiReferenceList implementsList = anInterface.getImplementsList();
      if (implementsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
      for (final PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        extendsList.add(referenceElement);
        referenceElement.delete();
      }
    }

    private static void moveSubClassExtendsToImplements(List<PsiClass> inheritors) {
      PsiClass oldClass = inheritors.get(0);
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(oldClass.getProject()).getElementFactory();
      final PsiJavaCodeReferenceElement classReference = elementFactory.createClassReferenceElement(oldClass);
      for (int i = 1; i < inheritors.size(); i++) {
        final PsiClass inheritor = inheritors.get(i);
        final PsiReferenceList extendsList = inheritor.getExtendsList();
        if (extendsList == null) {
          continue;
        }
        final PsiReferenceList implementsList = inheritor.getImplementsList();
        moveReference(extendsList, implementsList, classReference);
      }
    }

    private static void moveReference(@NotNull PsiReferenceList source, @Nullable PsiReferenceList target,
                                      @NotNull PsiJavaCodeReferenceElement reference) {
      final PsiJavaCodeReferenceElement[] sourceReferences = source.getReferenceElements();
      final String fqName = reference.getQualifiedName();
      for (final PsiJavaCodeReferenceElement sourceReference : sourceReferences) {
        final String implementsReferenceFqName = sourceReference.getQualifiedName();
        if (fqName.equals(implementsReferenceFqName)) {
          if (target != null) {
            target.add(sourceReference);
          }
          sourceReference.delete();
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassMayBeInterfaceVisitor();
  }

  private static class ClassMayBeInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter || aClass instanceof PsiAnonymousClass) {
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
        if (extendsElements.length > 0) {
          return false;
        }
      }
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      if (initializers.length > 0) {
        return false;
      }
      return allMethodsPublicAbstract(aClass) && allFieldsPublicStaticFinal(aClass) && allInnerClassesPublic(aClass);
    }

    private static boolean allFieldsPublicStaticFinal(PsiClass aClass) {
      boolean allFieldsStaticFinal = true;
      final PsiField[] fields = aClass.getFields();
      for (final PsiField field : fields) {
        if (!(field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)
              && field.hasModifierProperty(PsiModifier.PUBLIC))) {
          allFieldsStaticFinal = false;
        }
      }
      return allFieldsStaticFinal;
    }

    private static boolean allMethodsPublicAbstract(PsiClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      for (final PsiMethod method : methods) {
        if (!(method.hasModifierProperty(PsiModifier.ABSTRACT) && method.hasModifierProperty(PsiModifier.PUBLIC))) {
          return false;
        }
      }
      return true;
    }

    private static boolean allInnerClassesPublic(PsiClass aClass) {
      final PsiClass[] innerClasses = aClass.getInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (!innerClass.hasModifierProperty(PsiModifier.PUBLIC)) {
          return false;
        }
      }
      return true;
    }
  }
}
