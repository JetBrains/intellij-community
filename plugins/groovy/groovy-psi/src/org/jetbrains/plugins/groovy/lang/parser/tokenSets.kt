// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GroovyTokenSets")

package org.jetbrains.plugins.groovy.lang.parser

import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_BOOLEAN
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_BYTE
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_CHAR
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_DOUBLE
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_FLOAT
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_INT
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_LONG
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_SHORT
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_VOID

internal val primitiveTypes = TokenSet.create(
  KW_BOOLEAN,
  KW_BYTE,
  KW_CHAR,
  KW_DOUBLE,
  KW_FLOAT,
  KW_INT,
  KW_LONG,
  KW_SHORT,
  KW_VOID
)

