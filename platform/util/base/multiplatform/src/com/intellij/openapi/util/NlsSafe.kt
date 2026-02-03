// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

/**
 * Marker annotation for strings that don't require localization but still could be displayed in the UI.
 *
 * Examples:
 * - file name or path
 * - project name
 * - URL
 * - programming language or framework name
 *
 * Avoid using `@NlsSafe` just to suppress the "hardcoded string" inspection warning.
 * Use [@NonNls][org.jetbrains.annotations.NonNls] if something is not intended to be displayed to the user:
 * - internal identifier
 * - XML tag attribute
 * - substring to be searched in the external process output
 * - etc.
 *
 * This annotation is primarily intended to be used on variable or method return values (in "producer" context).
 * Avoid using it on method parameters (in "consumer" context), as this will simply allow calling the method with anything,
 * including hardcoded strings.
 */
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@Target(AnnotationTarget.TYPE,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.FIELD,
        AnnotationTarget.LOCAL_VARIABLE,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
)
annotation class NlsSafe
