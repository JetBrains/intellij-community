// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import org.jetbrains.plugins.github.api.data.GHNode

class SimpleGHPRIdentifier(id: String, override val number: Long) : GHNode(id), GHPRIdentifier {
  constructor(id: GHPRIdentifier) : this(id.id, id.number)
}
