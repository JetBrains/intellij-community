// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

data class SimpleGHPRIdentifier(override val id: String, override val number: Long) : GHPRIdentifier {
  constructor(id: GHPRIdentifier) : this(id.id, id.number)
}
