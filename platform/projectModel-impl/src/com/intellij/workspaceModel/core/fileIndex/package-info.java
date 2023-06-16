// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Classes in this package are used to determine which files belong to the workspace, and therefore should be processed by the platform 
 * (e.g., indexed). 
 * Each file is assigned one or more of {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind the kinds}. 
 * The kinds determine which of the standard scopes a file belongs to, and may affect how they are handled by the platform's code. 
 * 
 * <p>
 * The {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor contributors} are used to define kinds for the files
 * using information from {@link com.intellij.workspaceModel.ide.WorkspaceModel Workspace Model}'s entities. 
 * A contributor does that by registering {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet file sets}, which assign a
 * {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind kind} to a specific file or all files located under a specific 
 * directory, and by excluding specific files or directories from the file sets registered by this or other contributors.  
 * </p>
 * 
 * <p>
 * {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex WorkspaceFileIndex} is used to determine kinds assigned to a file.
 * A kind is assigned to the file if there is a file set of this kind pointing to the file or one of its parent directories, and the file
 * doesn't satisfy any exclusion rules defined for directories on the way to that parent. If no kinds are assigned to the file, it's 
 * considered as not included to the workspace. If there are file sets with different kinds pointing to the file or one of its parent
 * directories, and the file isn't excluded from them, all these kinds will be assigned to the file.
 * <br/>
 * For example, consider a file with path {@code "/a/b/c.txt"}:
 * <ul>
 *   <li>if {@code "/a"} is marked as a file set of 'content' kind, and {@code "/a/b"} is an exclusion root, the file doesn't belong to the workspace;</li>
 *   <li>if {@code "/a/b"} is marked as a file set of 'content' kind, and {@code "/a"} is an exclusion root, the file is part of the workspace with 'content' kind;</li>
 *   <li>if {@code "/a"} is marked as a file set of 'content' kind, and {@code "/a"} is an exclusion root, the file doesn't belong to the workspace;</li>
 *   <li>if {@code "/a"} is marked as a file set of 'content' kind, and {@code "/a/b"} is an file set of 'external' kind, the file with have two kinds: 'content' and 'external'.</li>
 * </ul>
 * </p>
 * 
 * <p>
 * File sets may store language-specific properties in custom {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData data}.   
 * </p>
 * <p>
 * For compatibility reasons, {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex WorkspaceFileIndex} also takes data from 
 * {@link com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy DirectoryIndexExcludePolicy} and 
 * {@link com.intellij.openapi.roots.AdditionalLibraryRootsProvider AdditionalLibraryRootsProvider} extensions. However, API of these 
 * extensions doesn't allow the platform to update the index incrementally, so they are considered as obsolete and shouldn't be used in the 
 * new code.
 * </p>
 * <p>
 * All classes in this package <strong>are experimental</strong> and their API may change in future versions.
 * </p>
 */
@ApiStatus.Experimental
package com.intellij.workspaceModel.core.fileIndex;

import org.jetbrains.annotations.ApiStatus;