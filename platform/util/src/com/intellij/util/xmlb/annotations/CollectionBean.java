// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Can read serialized JDOMExternalizableStringList, but in any case will be written in this bean format.
 * It is useful for application-level config, but for project-level consider to use ConverterProvider.
 *
 * Currently, only a string element type is supported.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface CollectionBean {
}
