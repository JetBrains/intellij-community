/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

public class MethodMayBeStaticInspectionBase extends BaseInspection {
  protected static final String IGNORE_DEFAULT_METHODS_ATTR_NAME = "m_ignoreDefaultMethods";
  protected static final String ONLY_PRIVATE_OR_FINAL_ATTR_NAME = "m_onlyPrivateOrFinal";
  protected static final String IGNORE_EMPTY_METHODS_ATTR_NAME = "m_ignoreEmptyMethods";
  protected static final String REPLACE_QUALIFIER_ATTR_NAME = "m_replaceQualifier";
  /**
   * @noinspection PublicField
   */
  public boolean m_onlyPrivateOrFinal = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreEmptyMethods = true;
  public boolean m_ignoreDefaultMethods = true;
  public boolean m_replaceQualifier = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("method.may.be.static.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("method.may.be.static.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodCanBeStaticVisitor();
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    node.addContent(new Element("option").setAttribute("name", ONLY_PRIVATE_OR_FINAL_ATTR_NAME).setAttribute("value", String.valueOf(m_onlyPrivateOrFinal)));
    node.addContent(new Element("option").setAttribute("name", IGNORE_EMPTY_METHODS_ATTR_NAME).setAttribute("value", String.valueOf(
      m_ignoreEmptyMethods)));
    if (!m_ignoreDefaultMethods) {
      node.addContent(new Element("option").setAttribute("name", IGNORE_DEFAULT_METHODS_ATTR_NAME).setAttribute("value", "false"));
    }
    if (!m_replaceQualifier) {
      node.addContent(new Element("option").setAttribute("name", REPLACE_QUALIFIER_ATTR_NAME).setAttribute("value", "false"));
    }
  }

  private class MethodCanBeStaticVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (method.hasModifierProperty(PsiModifier.STATIC) ||
          method.hasModifierProperty(PsiModifier.ABSTRACT) ||
          method.hasModifierProperty(PsiModifier.SYNCHRONIZED) ||
          method.hasModifierProperty(PsiModifier.NATIVE)) {
        return;
      }
      if (method.isConstructor() || method.getNameIdentifier() == null) {
        return;
      }
      if (m_ignoreDefaultMethods && method.hasModifierProperty(PsiModifier.DEFAULT)) {
        return;
      }
      if (m_ignoreEmptyMethods && MethodUtils.isEmpty(method)) {
        return;
      }
      final PsiClass containingClass = ClassUtils.getContainingClass(method);
      if (containingClass == null) {
        return;
      }
      final Condition<PsiElement>[] addins = InspectionManager.CANT_BE_STATIC_EXTENSION.getExtensions();
      for (Condition<PsiElement> addin : addins) {
        if (addin.value(method)) {
          return;
        }
      }
      final PsiElement scope = containingClass.getScope();
      if (!(scope instanceof PsiJavaFile) && !containingClass.hasModifierProperty(PsiModifier.STATIC) && !containingClass.isInterface()) {
        return;
      }
      if (m_onlyPrivateOrFinal && !method.hasModifierProperty(PsiModifier.FINAL) && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (isExcluded(method) || MethodUtils.hasSuper(method) || MethodUtils.isOverridden(method)) {
        return;
      }
      if (implementsSurprisingInterface(method)) {
        return;
      }
      final MethodReferenceVisitor visitor = new MethodReferenceVisitor(method);
      method.accept(visitor);
      if (!visitor.areReferencesStaticallyAccessible()) {
        return;
      }
      registerMethodError(method);
    }

    private boolean implementsSurprisingInterface(final PsiMethod method) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return false;
      }
      final Query<PsiClass> search = ClassInheritorsSearch.search(containingClass, method.getUseScope(), true, true, false);
      final boolean[] result = new boolean[1];
      search.forEach(new Processor<PsiClass>() {
        AtomicInteger count = new AtomicInteger(0);

        @Override
        public boolean process(PsiClass subClass) {
          if (count.incrementAndGet() > 5) {
            result[0] = true;
            return false;
          }
          final PsiReferenceList list = subClass.getImplementsList();
          if (list == null) {
            return true;
          }
          final PsiJavaCodeReferenceElement[] referenceElements = list.getReferenceElements();
          for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
            final PsiElement target = referenceElement.resolve();
            if (!(target instanceof PsiClass)) {
              result[0] = true;
              return false;
            }
            final PsiClass aClass = (PsiClass)target;
            if (!aClass.isInterface()) {
              result[0] = true;
              return false;
            }
            if (aClass.findMethodBySignature(method, true) != null) {
              result[0] = true;
              return false;
            }
          }
          return true;
        }
      });
      return result[0];
    }

    private boolean isExcluded(PsiMethod method) {
      return SerializationUtils.isWriteObject(method) || SerializationUtils.isReadObject(method) ||
             SerializationUtils.isWriteReplace(method) || SerializationUtils.isReadResolve(method);
    }
  }
}
