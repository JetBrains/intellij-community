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
package org.jetbrains.generate.tostring.config;

/**
 * This is a filtering pattern, used to filter unwanted fields for this action.
 */
public class FilterPattern {

    private String fieldName;
    private String fieldType;
    private String methodName;
    private String methodType;
    private boolean constantField;
    private boolean staticModifier;
    private boolean transientModifier;
    private boolean enumField;
    private boolean loggers;

    public String getFieldName() {
        return fieldName;
    }

    /**
     * Set's a filtering using regular expression on the field name.
     *
     * @param regexp the regular expression.
     */
    public void setFieldName(String regexp) {
        this.fieldName = regexp;
    }

    public boolean isConstantField() {
        return constantField;
    }

    /**
     * Set this to true to filter by constant fields.
     *
     * @param constantField if true constant fields is unwanted.
     */
    public void setConstantField(boolean constantField) {
        this.constantField = constantField;
    }

    public boolean isTransientModifier() {
        return transientModifier;
    }

    /**
     * Set this to true to filter by transient modifier.
     *
     * @param transientModifier if true fields with transient modifier is unwanted.
     */
    public void setTransientModifier(boolean transientModifier) {
        this.transientModifier = transientModifier;
    }

    public boolean isStaticModifier() {
        return staticModifier;
    }

    /**
     * Set this to true to filter by static modifier.
     *
     * @param staticModifier if true fields with static modifier is unwanted.
     */
    public void setStaticModifier(boolean staticModifier) {
        this.staticModifier = staticModifier;
    }

    public String getMethodName() {
        return methodName;
    }

    /**
     * Set's a filtering using regular expression on the method name.
     *
     * @param regexp the regular expression.
     */
    public void setMethodName(String regexp) {
        this.methodName = regexp;
    }

    public boolean isEnumField() {
        return enumField;
    }

    /**
     * Set this to true to filter by enum fields (JDK1.5).
     *
     * @param enumField if true enum fields is unwanted.
     * @since 3.17
     */
    public void setEnumField(boolean enumField) {
        this.enumField = enumField;
    }

    public boolean isLoggers() {
        return loggers;
    }

    /**
     * Set this to true to filter loggers (Log4j, JDK1.4).
     *
     * @param loggers if true logger fields is unwanted.
     * @since 3.20
     */
    public void setLoggers(boolean loggers) {
        this.loggers = loggers;
    }

    public String getFieldType() {
        return fieldType;
    }

    /**
     * Set's a filtering using the field type FQN.
     *
     * @param fieldType  the field type
     * @since 3.20
     */
    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getMethodType() {
        return methodType;
    }

    /**
     * Set's a filtering using the method return type FQN.
     *
     * @param methodType  the method return type
     * @since 3.20
     */
    public void setMethodType(String methodType) {
        this.methodType = methodType;
    }

    public String toString() {
        return "FilterPattern{" +
                "fieldName='" + fieldName + "'" +
                "fieldType='" + fieldType + "'" +
                ", methodName='" + methodName + "'" +
                ", methodType='" + methodType + "'" +
                ", constantField=" + constantField +
                ", staticModifier=" + staticModifier +
                ", transientModifier=" + transientModifier +
                ", enumField=" + enumField +
                ", loggers=" + loggers +
                "}";
    }


}
