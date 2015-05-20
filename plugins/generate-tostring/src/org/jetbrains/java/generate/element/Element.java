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
package org.jetbrains.java.generate.element;

import com.intellij.psi.PsiField;

/**
 * This is an Element.
 * <p/>
 * Element are all the fields and methods from a given class.
 * <p/>
 * The common information for the fields and the methods are what this element represent.
 */
public interface Element {

    /**
     * Get's the elements accessor.
     * <p/>
     * <br/>If the element is a field the accessor is just the of the field or name of it's accessor with () when useAccessor property is on
     * {@link ElementFactory#newFieldElement(PsiField, boolean)} - example: <code>myField</code> or <code>getMyField()</code>
     * <br/>If the element is a method the accessor is the methodname with () - example: <code>getMyField()</code>
     *
     * @return the accessor, null if none exists (only for a method with parameters).
     */
    String getAccessor();

    /**
     * Get's the elements name.
     *
     * @return name of this field.
     */
    String getName();

    /**
     * Is this element an array type?
     *
     * @return true if this element is an array type.
     */
    boolean isArray();

    boolean isNestedArray();

    /**
     * Is this element a {@link java.util.Collection} type (isAssignableFrom java.util.Collection)
     *
     * @return true if this element is a {@link java.util.Collection} type.
     */
    boolean isCollection();

    /**
     * Is this element a {@link java.util.Map} type (isAssignableFrom java.util.Map)
     *
     * @return true if this element is a {@link java.util.Map} type.
     */
    boolean isMap();

    /**
     * Is this element a primitive type
     *
     * @return true if this element is a primitive type.
     */
    boolean isPrimitive();

    /**
     * Is the element a {@link String} type?
     *
     * @return true if this element is a String type.
     */
    boolean isString();

    /**
     * Is the element a primitive array type? (int[], long[], float[] etc.).
     *
     * @return true if this element is a primitive array type.
     */
    boolean isPrimitiveArray();

    /**
     * Is the element an Object array type? (String[], Object[] etc.).
     *
     * @return true if this element is an Object array type.
     */
    boolean isObjectArray();

    /**
     * Is the element a numeric type?
     *
     * @return true if the element is a numeric type.
     */
    boolean isNumeric();

    /**
     * Is the element an Object type?
     *
     * @return true if the element is an Object type.
     */
    boolean isObject();

    /**
     * Is the element a java.util.Date type?
     *
     * @return true if the element is a Date type.
     */
    boolean isDate();

    /**
     * Is this element a {@link java.util.Set} type (isAssignableFrom java.util.Set)
     *
     * @return true if this element is a {@link java.util.Set} type.
     */
    boolean isSet();

    /**
     * Is this element a {@link java.util.List} type (isAssignableFrom java.util.List)
     *
     * @return true if this element is a {@link java.util.List} type.
     */
    boolean isList();

    /**
     * Is the element a String array type (etc. String[])?
     *
     * @return true if the element is a String array type.
     */
    boolean isStringArray();

    /**
     * Is the element a java.util.Calendar type (also sublcass such as GregorianCalendar).
     *
     * @return   true if the element is a java.util.Calendar type.
     */
    boolean isCalendar();

    /**
     * Is the element a java.lang.Boolean type or a boolean primitive?
     *
     * @return   true if the element is either a java.lang.Boolean or a boolean primitive
     */
    boolean isBoolean();

    boolean isLong();
    boolean isShort();
    boolean isChar();
    boolean isFloat();
    boolean isDouble();
    boolean isByte();
    boolean isVoid();
    boolean isNotNull();

    /**
     * Get's the elements type classname (etc. Object, String, List)
     *
     * @return  the elements type classname.
     */
    String getTypeName();

    /**
     * Get's the elements type qualified classname (etc. java.lang.Object, java.lang.String, java.util.List)
     *
     * @return  the elements type qualified classname.
     */
    String getTypeQualifiedName();

  /**
   * 
   * @return type canonical text
   */
    String getType();

}
