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

import com.intellij.openapi.util.text.StringUtil;

/**
 * This is a method element containing information about the method.
 *
 * @see ElementFactory
 */
@SuppressWarnings({"UnusedDeclaration"})
public class MethodElement extends AbstractElement implements Element {

    private String methodName;
    private String fieldName;
    private boolean modifierAbstract;
    private boolean modifierSynchronized;
    private boolean returnTypeVoid;
    private boolean getter;
    private boolean deprecated;

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

    @Override
    public String getAccessor() {
        return methodName + "()";
    }

    public boolean isModifierAbstract() {
        return modifierAbstract;
    }

    public void setModifierAbstract(boolean modifierAbstract) {
        this.modifierAbstract = modifierAbstract;
    }

  /**
   * Exists for compatibility with old templates
   */
    public boolean isModifierSynchronzied() {
        return isModifierSynchronized();
    }

    public boolean isModifierSynchronized() {
        return modifierSynchronized;
    }

    public void setModifierSynchronized(boolean modifierSynchronized) {
        this.modifierSynchronized = modifierSynchronized;
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

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    /**
     * Performs a regular expression matching the methodname.
     *
     * @param regexp regular expression.
     * @return true if the methodname matches the regular expression.
     * @throws IllegalArgumentException is throw if the given input is invalid (an empty String) or a pattern matching error.
     */
    public boolean matchName(String regexp) throws IllegalArgumentException {
        if (StringUtil.isEmpty(regexp)) {
            throw new IllegalArgumentException("Can't perform regular expression since the given input is empty. Check the Method body velocity code: regexp='" + regexp + "'");
        }

        return methodName.matches(regexp);
    }

    public String toString() {
        return super.toString() + " ::: MethodElement{" +
                "fieldName='" + fieldName + "'" +
                ", methodName='" + methodName + "'" +
                ", modifierAbstract=" + modifierAbstract +
                ", modifierSynchronized=" + modifierSynchronized +
                ", returnTypeVoid=" + returnTypeVoid +
                ", getter=" + getter +
                ", deprecated=" + deprecated +
                "}";
    }
}
