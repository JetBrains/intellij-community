// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

fun argumentsString(arguments: Arguments): String {
  return arguments.joinToString(", ", "(", ")") {
    it.type?.internalCanonicalText ?: "?"
  }
}
