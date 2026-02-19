// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.annotations

/**
 * Name property values acts as implicit field of parent object.
 *
 * By setting name of some object, you are actually declare implicit field
 * of parent object with same name and this object as value.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Name
