// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.vcs.impl.shared.rhizome.NodeEntity
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
/**
 * Representing a grouped list of changes inside a single changelist.
 */
class ChangeList(val changeListNode: NodeEntity, val changes: List<NodeEntity>)

@ApiStatus.Internal
val CHANGE_LISTS_KEY: DataKey<List<ChangeList>> = DataKey.create("Frontend.VCS.Changes")