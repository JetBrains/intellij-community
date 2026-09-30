// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.annotations

/**
 * Mark a property in an entity to be indexed in [VirtualFileUrlIndex][com.intellij.platform.workspace.storage.url.VirtualFileUrlIndex].
 * 
 * If an annotated property is [VirtualFileUrl][com.intellij.platform.workspace.storage.url.VirtualFileUrl] or a list of VirtualFileUrls, 
 * its value is indexed. If it is a data class, its properties are treated like if they are @IndexVfu annotated entity properties.
 * @see [com.intellij.platform.workspace.storage.url.VirtualFileUrlIndex.findEntitiesByUrl]
 */
@Target(AnnotationTarget.PROPERTY)
public annotation class IndexVfu
