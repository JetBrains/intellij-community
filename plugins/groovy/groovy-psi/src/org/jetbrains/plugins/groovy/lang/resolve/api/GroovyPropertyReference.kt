// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference

interface GroovyPropertyReference : GroovyReference {

  val receiverArgument: Argument?

  val propertyName: String
}
