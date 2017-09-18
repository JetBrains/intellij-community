/*
 * Copyright 2001-2013 the original author or authors.
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
package org.jetbrains.java.generate.element;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.java.generate.psi.PsiAdapter;

/**
 * Factory for creating {@link FieldElement} or {@link ClassElement} objects.
 */
public class ElementFactory {

  private static final Logger log = Logger.getInstance("#ElementFactory");

  private ElementFactory() {
  }

  /**
   * Creates a new {@link ClassElement} object.
   *
   * @param clazz   class information.
   * @return a new {@link ClassElement} object.
   */
  public static ClassElement newClassElement(PsiClass clazz) {
    ClassElement ce = new ClassElement();

    // name
    ce.setName(clazz.getName());
    ce.setQualifiedName(clazz.getQualifiedName());

    // super
    PsiClass superClass = clazz.getSuperClass();
    if (superClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
      ce.setSuperName(superClass.getName());
    }

    // interfaces
    ce.setImplementNames(PsiAdapter.getImplementsClassnames(clazz));

    // other
    ce.setEnum(clazz.isEnum());
    ce.setDeprecated(clazz.isDeprecated());
    ce.setException(PsiAdapter.isExceptionClass(clazz));
    ce.setAbstract(clazz.hasModifierProperty(PsiModifier.ABSTRACT));
    ce.setTypeParams(clazz.getTypeParameters().length);

    return ce;
  }

  /**
   * Create a new {@link FieldElement} object.
   *
   * @param field   the {@link com.intellij.psi.PsiField} to get the information from.
   * @return a new {@link FieldElement} object.
   */
  public static FieldElement newFieldElement(PsiField field, boolean useAccessor) {
    FieldElement fe = new FieldElement();
    fe.setName(field.getName());
    final PsiMethod getterForField = useAccessor ? PropertyUtilBase.findGetterForField(field) : null;
    fe.setAccessor(getterForField != null ? getterForField.getName() + "()" : field.getName());

    if (PsiAdapter.isConstantField(field)) fe.setConstant(true);
    if (PsiAdapter.isEnumField(field)) fe.setEnum(true);
    PsiModifierList modifiers = field.getModifierList();
    if (modifiers != null) {
      if (modifiers.hasModifierProperty(PsiModifier.TRANSIENT)) fe.setModifierTransient(true);
      if (modifiers.hasModifierProperty(PsiModifier.VOLATILE)) fe.setModifierVolatile(true);
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(field.getProject()).getElementFactory();
    PsiType type = field.getType();
    setElementInfo(fe, factory, type, modifiers);

    return fe;
  }

  /**
   * Creates a new {@link MethodElement} object.
   *
   * @param method  the PSI method object.
   * @return a new {@link MethodElement} object.
   * @since 2.15
   */
  public static MethodElement newMethodElement(PsiMethod method) {
    MethodElement me = new MethodElement();
    PsiType type = method.getReturnType();
    PsiModifierList modifiers = method.getModifierList();

    // if something is wrong:
    // http://www.intellij.net/forums/thread.jsp?nav=false&forum=18&thread=88676&start=0&msRange=15
    if (type == null) {
      log.warn("This method does not have a valid return type: " + method.getName() + ", returnType=" + type);
      return me;
    }
    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
    setElementInfo(me, factory, type, modifiers);

    // names
    String fieldName = PsiAdapter.getGetterFieldName(method);
    me.setName(fieldName == null ? method.getName() : fieldName);
    me.setFieldName(fieldName);
    me.setMethodName(method.getName());

    // getter
    me.setGetter(PsiAdapter.isGetterMethod(method));

    // misc
    me.setDeprecated(method.isDeprecated());
    me.setReturnTypeVoid(PsiAdapter.isTypeOfVoid(method.getReturnType()));

    // modifiers
    if (modifiers.hasModifierProperty(PsiModifier.ABSTRACT)) me.setModifierAbstract(true);
    if (modifiers.hasModifierProperty(PsiModifier.SYNCHRONIZED)) me.setModifierSynchronized(true);

    return me;
  }

  /**
   * Sets the basic element information from the given type.
   *
   * @param element   the element to set information from the type
   * @param factory
   * @param type      the type
   * @param modifiers modifier list
   * @since 2.15
   */
  private static void setElementInfo(AbstractElement element,
                                     PsiElementFactory factory,
                                     PsiType type,
                                     PsiModifierList modifiers) {

    // type names
    element.setTypeName(PsiAdapter.getTypeClassName(type));
    element.setTypeQualifiedName(PsiAdapter.getTypeQualifiedClassName(type));
    element.setType(type.getCanonicalText());

    // arrays, collections and maps types
    if (PsiAdapter.isObjectArrayType(type)) {
      element.setObjectArray(true);
      element.setArray(true);

      // additional specify if the element is a string array
      if (PsiAdapter.isStringArrayType(type)) element.setStringArray(true);

    }
    else if (PsiAdapter.isPrimitiveArrayType(type)) {
      element.setPrimitiveArray(true);
      element.setArray(true);
    }
    if (PsiAdapter.isCollectionType(factory, type)) element.setCollection(true);
    if (PsiAdapter.isListType(factory, type)) element.setList(true);
    if (PsiAdapter.isSetType(factory, type)) element.setSet(true);
    if (PsiAdapter.isMapType(factory, type)) element.setMap(true);

    // other types
    if (PsiAdapter.isPrimitiveType(type)) element.setPrimitive(true);
    if (PsiAdapter.isObjectType(factory, type)) element.setObject(true);
    if (PsiAdapter.isStringType(factory, type)) element.setString(true);
    if (PsiAdapter.isNumericType(factory, type)) element.setNumeric(true);
    if (PsiAdapter.isDateType(factory, type)) element.setDate(true);
    if (PsiAdapter.isCalendarType(factory, type)) element.setCalendar(true);
    if (PsiAdapter.isBooleanType(factory, type)) element.setBoolean(true);
    if (PsiType.VOID.equals(type)) element.setVoid(true);
    if (PsiType.LONG.equals(type)) element.setLong(true);
    if (PsiType.FLOAT.equals(type)) element.setFloat(true);
    if (PsiType.DOUBLE.equals(type)) element.setDouble(true);
    if (PsiType.BYTE.equals(type)) element.setByte(true);
    if (PsiType.CHAR.equals(type)) element.setChar(true);
    if (PsiType.SHORT.equals(type)) element.setShort(true);
    element.setNestedArray(PsiAdapter.isNestedArray(type));
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
