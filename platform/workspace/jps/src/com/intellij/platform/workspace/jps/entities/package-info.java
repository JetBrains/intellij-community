// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * This package contains interfaces which describe concepts from the old project model in terms of {@link com.intellij.platform.workspace.storage workspace model}
 * entities. They are used inside so-called 'legacy bridge' implementations of {@link com.intellij.openapi.module.ModuleManager ModuleManager},
 * {@link com.intellij.openapi.roots.ModuleRootManager ModuleRootManager}, {@link com.intellij.openapi.roots.libraries.Library Library} and
 * other interfaces to store data inside the workspace model storage.
 * <p>
 * Entities from this package can be used to read and modify data stored in the workspace model directly via  
 * {@link com.intellij.platform.backend.workspace.WorkspaceModel WorkspaceModel}. This works faster than using the old API, there is no need
 * to take the read lock for reading, and you'll never hit "already disposed" problem. 
 * <p>
 * However, if you want to store data specific for some language, framework, or technology, it's better to define a new type of entities by 
 * extending {@link com.intellij.platform.workspace.storage.WorkspaceEntity WorkspaceEntity} interface instead of reusing 
 * {@link com.intellij.platform.workspace.jps.entities.ModuleEntity}, {@link com.intellij.platform.workspace.jps.entities.ContentRootEntity}
 * or other entities from this package. This way you can easily represent properties specific for your domain, and won't get problems from
 * other code which may use the common entities in a way which isn't expected by you.  
 */
package com.intellij.platform.workspace.jps.entities;