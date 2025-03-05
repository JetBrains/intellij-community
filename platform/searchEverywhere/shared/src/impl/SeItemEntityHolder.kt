// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.platform.searchEverywhere.api.SeItem
import com.jetbrains.rhizomedb.*
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Serializable
class SeItemEntityHolder(override val eid: EID) : Entity {
  val item: SeItem?
    get() = this[Item] as? SeItem

  @ApiStatus.Internal
  companion object : EntityType<SeItemEntityHolder>(SeItemEntityHolder::class.java.name, "com.intellij", {
    SeItemEntityHolder(it)
  }) {
    internal val Item = requiredTransient<Any>("item")
    internal val Entity = requiredRef<SeItemEntity>("itemEntity", RefFlags.CASCADE_DELETE_BY)
  }
}