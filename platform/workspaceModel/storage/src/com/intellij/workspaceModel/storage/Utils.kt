// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.EntityId

// TODO: 28.05.2021 Make this value class since kt 1.5
// Just a wrapper for entity id in THIS store
internal data class ThisEntityId(val id: EntityId)

// Just a wrapper for entity id in some other store
internal data class NotThisEntityId(val id: EntityId)

internal fun EntityId.asThis(): ThisEntityId = ThisEntityId(this)
internal fun EntityId.notThis(): NotThisEntityId = NotThisEntityId(this)

internal fun currentStackTrace(depth: Int): String = Throwable().stackTrace.take(depth).joinToString(separator = "\n") { it.toString() }

fun loadClassByName(name: String, classLoader: ClassLoader): Class<*> {
  if (name.startsWith("[")) return Class.forName(name)
  return classLoader.loadClass(name)
}
