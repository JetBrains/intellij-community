// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core

import java.util.*

class CollectingNameValidator @JvmOverloads constructor(
  existingNames: Collection<String> = Collections.emptySet(),
  private val filter: (String) -> Boolean = { true }
) : (String) -> Boolean {
    private val existingNames = HashSet(existingNames)

    override fun invoke(name: String): Boolean {
        if (name !in existingNames && filter(name)) {
            existingNames.add(name)
            return true
        }
        return false
    }

    fun addName(name: String) {
        existingNames.add(name)
    }
}