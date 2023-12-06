// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.annotations

/**
 * Marks an interface extending [WorkspaceEntity][com.intellij.platform.workspace.storage.WorkspaceEntity] as abstract. 
 * It won't be possible to create an entity of that type, only of its non-abstract subtypes.
 */
@Target(AnnotationTarget.CLASS)
public annotation class Abstract