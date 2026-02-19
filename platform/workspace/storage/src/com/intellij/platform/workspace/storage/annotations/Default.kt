// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.annotations

/**
 * Mark a property in an entity interface which has a default value provided by its getter.
 * Without this annotation, a property with a getter is treated as a read-only computable property.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
public annotation class Default
