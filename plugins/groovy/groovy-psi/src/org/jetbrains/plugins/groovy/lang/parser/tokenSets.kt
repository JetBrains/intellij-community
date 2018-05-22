// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GroovyTokenSets")

package org.jetbrains.plugins.groovy.lang.parser

import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*

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

internal val assignments = TokenSet.create(
  T_ASSIGN,
  T_POW_ASSIGN,
  T_STAR_ASSIGN,
  T_DIV_ASSIGN,
  T_REM_ASSIGN,
  T_PLUS_ASSIGN,
  T_MINUS_ASSIGN,
  T_LSH_ASSIGN,
  T_RSH_ASSIGN,
  T_RSHU_ASSIGN,
  T_BAND_ASSIGN,
  T_XOR_ASSIGN,
  T_BOR_ASSIGN
)

