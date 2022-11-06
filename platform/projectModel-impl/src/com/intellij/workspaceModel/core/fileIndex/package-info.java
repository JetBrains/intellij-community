// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Classes in this package provide a way to associate files and directories with entities from {@link com.intellij.workspaceModel.ide.WorkspaceModel}.
 * <p>
 * Use {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor} to include files referenced from entities to the
 * workspace, and use {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex} to get status of a file or directory in
 * the workspace.
 * </p>
 * 
 * <p>
 * All classes in this package <strong>are experimental</strong> and their API will change in future versions.
 * </p>
 */
@ApiStatus.Experimental
package com.intellij.workspaceModel.core.fileIndex;

import org.jetbrains.annotations.ApiStatus;