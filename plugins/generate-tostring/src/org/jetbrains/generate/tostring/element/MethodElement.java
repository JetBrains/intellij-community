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

import org.jetbrains.generate.tostring.config.FilterPattern;
import org.jetbrains.generate.tostring.config.Filterable;
import org.jetbrains.generate.tostring.util.StringUtil;

import java.io.Serializable;

/**
 * This is a method element containing information about the method.
 *
 * @see ElementFactory
 */
public class MethodElement extends AbstractElement implements Serializable, Element, Filterable {

  private String methodName;
  private String fieldName;
  private boolean modifierAbstract;
  private boolean returnTypeVoid;
  private boolean getter;

  public String getMethodName() {
    return methodName;
  }

  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  public String getAccessor() {
    return methodName + "()";
  }

  public boolean isModifierAbstract() {
    return modifierAbstract;
  }

  public void setModifierAbstract(boolean modifierAbstract) {
    this.modifierAbstract = modifierAbstract;
  }

  public boolean isReturnTypeVoid() {
    return returnTypeVoid;
  }

  public void setReturnTypeVoid(boolean returnTypeVoid) {
    this.returnTypeVoid = returnTypeVoid;
  }

  public boolean isGetter() {
    return getter;
  }

  public void setGetter(boolean getter) {
    this.getter = getter;
  }

  public boolean applyFilter(FilterPattern pattern) {
    if (pattern == null) {
      return false;
    }

    if (StringUtil.isNotEmpty(pattern.getMethodName()) && methodName.matches(pattern.getMethodName())) {
      return true;
    }

    if (StringUtil.isNotEmpty(pattern.getMethodType()) && !returnTypeVoid && !isPrimitive && getTypeQualifiedName() != null) {
      String type = getTypeQualifiedName();
      if (type.matches(pattern.getMethodType())) {
        return true;
      }
    }

    return false;
  }

  public String toString() {
    return super.toString() + " ::: MethodElement{" +
           "fieldName='" + fieldName + "'" +
           ", methodName='" + methodName + "'" +
           ", modifierAbstract=" + modifierAbstract +
           ", returnTypeVoid=" + returnTypeVoid +
           ", getter=" + getter +
           "}";
  }
}
