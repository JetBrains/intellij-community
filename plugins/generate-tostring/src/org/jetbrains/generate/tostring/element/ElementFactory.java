/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.generate.tostring.element;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.generate.tostring.psi.PsiAdapter;

/**
 * Factory for creating {@link FieldElement} or {@link ClassElement} objects.
 */
public class ElementFactory {

  private static final Logger log = Logger.getInstance("#org.jetbrains.generate.tostring.element.ElementFactory");

  private ElementFactory() {
  }

  /**
   * Creates a new {@link ClassElement} object.
   *
   * @param project the IDEA project.
   * @param clazz   class information.
   * @param psi     the psi adapter
   * @return a new {@link ClassElement} object.
   */
  public static ClassElement newClassElement(Project project, PsiClass clazz, PsiAdapter psi) {
    ClassElement ce = new ClassElement();

    // name
    ce.setName(clazz.getName());
    ce.setQualifiedName(clazz.getQualifiedName());

    // super
    ce.setHasSuper(psi.hasSuperClass(project, clazz));
    PsiClass superClass = psi.getSuperClass(project, clazz);
    ce.setSuperName(superClass == null ? null : superClass.getName());
    ce.setSuperQualifiedName(superClass == null ? null : superClass.getQualifiedName());

    // interfaces
    ce.setImplementNames(psi.getImplementsClassnames(clazz));

    // other
    ce.setEnum(clazz.isEnum());
    ce.setDeprecated(clazz.isDeprecated());
    ce.setException(PsiAdapter.isExceptionClass(clazz));
    ce.setAbstract(psi.isAbstractClass(clazz));

    return ce;
  }

  /**
   * Create a new {@link FieldElement} object.
   *
   * @param project the IDEA project.
   * @param field   the {@link com.intellij.psi.PsiField} to get the information from.
   * @param psi     the psi adapter
   * @return a new {@link FieldElement} object.
   */
  public static FieldElement newFieldElement(Project project, PsiField field, PsiAdapter psi) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    FieldElement fe = new FieldElement();
    PsiType type = field.getType();

    fe.setName(field.getName());

    if (psi.isConstantField(field)) fe.setConstant(true);
    if (psi.isEnumField(field)) fe.setEnum(true);
    PsiModifierList modifiers = field.getModifierList();
    if (modifiers != null) {
      if (modifiers.hasModifierProperty(PsiModifier.TRANSIENT)) fe.setModifierTransient(true);
      if (modifiers.hasModifierProperty(PsiModifier.VOLATILE)) fe.setModifierVolatile(true);
    }

    setElementInfo(fe, factory, type, modifiers, psi);

    return fe;
  }

  /**
   * Creates a new {@link MethodElement} object.
   *
   * @param method  the PSI method object.
   * @param factory the PsiAdapterFactory.
   * @param psi     the psi adapter
   * @return a new {@link MethodElement} object.
   * @since 2.15
   */
  public static MethodElement newMethodElement(PsiMethod method, PsiElementFactory factory, PsiAdapter psi) {
    MethodElement me = new MethodElement();
    PsiType type = method.getReturnType();
    PsiModifierList modifiers = method.getModifierList();

    // if something is wrong:
    // http://www.intellij.net/forums/thread.jsp?nav=false&forum=18&thread=88676&start=0&msRange=15
    if (type == null) {
      log.warn("This method does not have a valid return type: " + method.getName() + ", returnType=" + type);
      return me;
    }

    setElementInfo(me, factory, type, modifiers, psi);

    // names
    String fieldName = psi.getGetterFieldName(factory, method);
    me.setName(fieldName == null ? method.getName() : fieldName);
    me.setFieldName(fieldName);
    me.setMethodName(method.getName());

    // getter
    me.setGetter(psi.isGetterMethod(factory, method));

    // misc
    me.setReturnTypeVoid(psi.isTypeOfVoid(method.getReturnType()));
    me.setDeprecated(method.isDeprecated());

    // modifiers
    if (modifiers.hasModifierProperty(PsiModifier.ABSTRACT)) me.setModifierAbstract(true);
    if (modifiers.hasModifierProperty(PsiModifier.SYNCHRONIZED)) me.setModifierSynchronzied(true);

    return me;
  }

  /**
   * Sets the basic element information from the given type.
   *
   * @param element   the element to set information from the type
   * @param factory   the PsiAdapterFactory.
   * @param type      the type
   * @param psi       the psi adapter.
   * @param modifiers modifier list
   * @since 2.15
   */
  private static void setElementInfo(AbstractElement element,
                                     PsiElementFactory factory,
                                     PsiType type,
                                     PsiModifierList modifiers,
                                     PsiAdapter psi) {

    // type names
    element.setTypeName(psi.getTypeClassName(type));
    element.setTypeQualifiedName(psi.getTypeQualifiedClassName(type));

    // arrays, collections and maps types
    if (psi.isObjectArrayType(type)) {
      element.setObjectArray(true);
      element.setArray(true);

      // additional specify if the element is a string array
      if (psi.isStringArrayType(type)) element.setStringArray(true);

    }
    else if (psi.isPrimitiveArrayType(type)) {
      element.setPrimitiveArray(true);
      element.setArray(true);
    }
    if (psi.isCollectionType(factory, type)) element.setCollection(true);
    if (psi.isListType(factory, type)) element.setList(true);
    if (psi.isSetType(factory, type)) element.setSet(true);
    if (psi.isMapType(factory, type)) element.setMap(true);

    // other types
    if (psi.isPrimitiveType(type)) element.setPrimitive(true);
    if (psi.isObjectType(factory, type)) element.setObject(true);
    if (psi.isStringType(factory, type)) element.setString(true);
    if (psi.isNumericType(factory, type)) element.setNumeric(true);
    if (psi.isDateType(factory, type)) element.setDate(true);
    if (psi.isCalendarType(factory, type)) element.setCalendar(true);
    if (psi.isBooleanType(factory, type)) element.setBoolean(true);

    // modifiers
    if (modifiers != null) {
      if (modifiers.hasModifierProperty(PsiModifier.STATIC)) element.setModifierStatic(true);
      if (modifiers.hasModifierProperty(PsiModifier.FINAL)) element.setModifierFinal(true);
      if (modifiers.hasModifierProperty(PsiModifier.PUBLIC)) {
        element.setModifierPublic(true);
      }
      else if (modifiers.hasModifierProperty(PsiModifier.PROTECTED)) {
        element.setModifierProtected(true);
      }
      else if (modifiers.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        element.setModifierPackageLocal(true);
      }
      else if (modifiers.hasModifierProperty(PsiModifier.PRIVATE)) element.setModifierPrivate(true);
    }

  }

}
