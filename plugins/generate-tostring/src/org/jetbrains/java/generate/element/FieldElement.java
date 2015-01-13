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
 * This is a field element containing information about the field.
 *
 * @see ElementFactory
 */
@SuppressWarnings({"JavaDoc"})
public class FieldElement extends AbstractElement implements Element {

    private boolean isConstant;
    private boolean isEnum;

    private boolean isModifierTransient;
    private boolean isModifierVolatile;
    private String accessor;

  public String getAccessor() {
        return accessor;
    }

    /**
     * Is the field a constant type?
     */
    public boolean isConstant() {
        return isConstant;
    }

    /**
     * Does the field have a transient modifier?
     */
    public boolean isModifierTransient() {
        return isModifierTransient;
    }

    /**
     * Does the field have a volatile modifier?
     */
    public boolean isModifierVolatile() {
        return isModifierVolatile;
    }

    /**
     * Is the field an enum type (JDK1.5)?
     * @since 3.17
     */
    public boolean isEnum() {
        return isEnum;
    }

    void setConstant(boolean constant) {
        isConstant = constant;
    }

    void setModifierTransient(boolean modifierTransient) {
        isModifierTransient = modifierTransient;
    }

    void setModifierVolatile(boolean modifierVolatile) {
         this.isModifierVolatile = modifierVolatile;
    }

    public void setEnum(boolean anEnum) {
        isEnum = anEnum;
    }

    /**
     * Performs a regular expression matching the fieldname.
     *
     * @param regexp regular expression.
     * @return true if the fieldname matches the regular expression.
     * @throws IllegalArgumentException is throw if the given input is invalid (an empty String) or a pattern matching error.
     */
    public boolean matchName(String regexp) throws IllegalArgumentException {
        if (StringUtil.isEmpty(regexp)) {
            throw new IllegalArgumentException("Can't perform regular expression since the given input is empty. Check the Method body velocity code: regexp='" + regexp + "'");
        }

        return name.matches(regexp);
    }

    public String toString() {
        return super.toString() + " ::: FieldElement{" +
                "isConstant=" + isConstant +
                ", isEnum=" + isEnum +
                ", isModifierTransient=" + isModifierTransient +
                ", isModifierVolatile=" + isModifierVolatile +
                "}";
    }

  public void setAccessor(String accessor) {
    this.accessor = accessor;
  }
}