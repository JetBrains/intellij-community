/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.memory.InnerClassReferenceVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ClassMayBeInterfaceInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean reportClassesWithNonAbstractMethods = false;

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

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("class.may.be.interface.java8.option"), this,
                                          "reportClassesWithNonAbstractMethods");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (reportClassesWithNonAbstractMethods) {
      node.addContent(new Element("option").setAttribute("name", "reportClassesWithNonAbstractMethods").setAttribute("value", "true"));
    }
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
      for (PsiMethod method : aClass.getMethods()) {
        PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, false);
        if (method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          continue;
        }
        PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, true);
      }
      for (PsiField field : aClass.getFields()) {
        PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, false);
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, false);
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, false);
      }
      for (PsiClass innerClass : aClass.getInnerClasses()) {
        PsiUtil.setModifierProperty(innerClass, PsiModifier.PUBLIC, false);
      }
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
      PsiUtil.setModifierProperty(aClass, PsiModifier.ABSTRACT, false);
      PsiUtil.setModifierProperty(aClass, PsiModifier.FINAL, false);
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
      final PsiClass oldClass = inheritors.get(0);
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

  private class ClassMayBeInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter || aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (!mayBeInterface(aClass)) {
        return;
      }
      if (ClassUtils.isInnerClass(aClass)) {
        final InnerClassReferenceVisitor visitor = new InnerClassReferenceVisitor(aClass);
        aClass.accept(visitor);
        if (!visitor.canInnerClassBeStatic()) {
          return;
        }
      }
      registerClassError(aClass);
    }

    public boolean mayBeInterface(PsiClass aClass) {
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

    private boolean allFieldsPublicStaticFinal(PsiClass aClass) {
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

    private boolean allMethodsPublicAbstract(PsiClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      for (final PsiMethod method : methods) {
        if (!method.hasModifierProperty(PsiModifier.ABSTRACT) &&
            (!reportClassesWithNonAbstractMethods || !PsiUtil.isLanguageLevel8OrHigher(aClass))) {
          return false;
        }
        else if (!method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.FINAL)) {
          return false;
        }
      }
      return true;
    }

    private boolean allInnerClassesPublic(PsiClass aClass) {
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
