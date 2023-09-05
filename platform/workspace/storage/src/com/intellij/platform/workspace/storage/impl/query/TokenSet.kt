// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.query

/**
 * Collection of tokens grouped by operation type
 */
internal class TokenSet(initialTokens: Iterable<Token>) {

  constructor(): this(emptySet())

  private val addedTokens: MutableList<Token> = mutableListOf()
  private val removedTokens: MutableList<Token> = mutableListOf()

  init {
    initialTokens.forEach {
      add(it)
    }
  }

  fun add(token: Token) {
    when (token.operation) {
      Operation.ADDED -> addedTokens += token
      Operation.REMOVED -> removedTokens += token
    }
  }

  fun addedTokens(): List<Token> {
    return addedTokens
  }

  fun removedTokens(): List<Token> {
    return removedTokens
  }

  operator fun plusAssign(token: Token) = add(token)
  operator fun plusAssign(tokens: Iterable<Token>) {
    tokens.forEach { token -> add(token) }
  }
}
