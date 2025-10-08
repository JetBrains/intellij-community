// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.LexerPosition
import com.intellij.util.ThreadLocalKmp

import kotlin.jvm.JvmField

open class LayeredLexer(baseLexer: Lexer) : DelegateLexer(baseLexer) {
  private var state = 0
  private val startTokenToLayerLexerMap = HashMap<SyntaxElementType, Lexer>()
  private var currentLayerLexer: Lexer? = null
  private var currentBaseTokenType: SyntaxElementType? = null
  private var layerLeftPart = -1
  private var baseTokenEnd = -1

  private val selfStoppingLexersSet = HashSet<Lexer>(1)
  private val stopTokensMap = HashMap<Lexer, Array<SyntaxElementType>>(1)

  fun registerSelfStoppingLayer(
    lexer: Lexer,
    startTokens: Array<SyntaxElementType>,
    stopTokens: Array<SyntaxElementType>,
  ) {
    if (DISABLE_LAYERS_FLAG.get() == true) return
    registerLayer(lexer, *startTokens)
    selfStoppingLexersSet.add(lexer)
    stopTokensMap[lexer] = stopTokens
  }

  fun registerLayer(lexer: Lexer, vararg startTokens: SyntaxElementType) {
    if (DISABLE_LAYERS_FLAG.get() == true) return
    for (startToken in startTokens) {
      startTokenToLayerLexerMap[startToken] = lexer
    }
  }

  private fun activateLayerIfNecessary() {
    val baseTokenType = super.getTokenType()
    currentLayerLexer = findLayerLexer(baseTokenType)
    if (currentLayerLexer != null) {
      currentBaseTokenType = baseTokenType
      baseTokenEnd = super.getTokenEnd()
      currentLayerLexer!!.start(super.getBufferSequence(), super.getTokenStart(), super.getTokenEnd())
      if (currentLayerLexer in selfStoppingLexersSet) super.advance()
    }
  }

  protected open fun findLayerLexer(type: SyntaxElementType?): Lexer? = startTokenToLayerLexerMap[type]

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    state = initialState
    currentLayerLexer = null

    super.start(buffer, startOffset, endOffset, initialState)
    activateLayerIfNecessary()
  }

  override fun getState(): Int = state
  override fun getTokenType(): SyntaxElementType? = when {
    isInLayerEndGap -> currentBaseTokenType
    isLayerActive -> currentLayerLexer!!.getTokenType()
    else -> super.getTokenType()
  }

  override fun getTokenStart(): Int = when {
    isInLayerEndGap -> layerLeftPart
    isLayerActive -> currentLayerLexer!!.getTokenStart()
    else -> super.getTokenStart()
  }

  override fun getTokenEnd(): Int = when {
    isInLayerEndGap -> baseTokenEnd
    isLayerActive -> currentLayerLexer!!.getTokenEnd()
    else -> super.getTokenEnd()
  }

  override fun advance() {
    if (isInLayerEndGap) {
      layerLeftPart = -1
      state = super.getState()
      return
    }

    if (isLayerActive) {
      val activeLayerLexer = currentLayerLexer
      var layerTokenType = activeLayerLexer!!.getTokenType()
      if (!currentLayerLexer!!.isStopToken(layerTokenType)) {
        currentLayerLexer!!.advance()
        layerTokenType = currentLayerLexer!!.getTokenType()
      }
      else {
        layerTokenType = null
      }
      if (layerTokenType == null) {
        val tokenEnd = currentLayerLexer!!.getTokenEnd()
        val isSelfStopping = activeLayerLexer in selfStoppingLexersSet
        currentLayerLexer = null
        if (!isSelfStopping) {
          super.advance()
        }
        else if (tokenEnd != baseTokenEnd) {
          state = IN_LAYER_LEXER_FINISHED_STATE
          layerLeftPart = tokenEnd
          return
        }
        activateLayerIfNecessary()
      }
    }
    else {
      super.advance()
      activateLayerIfNecessary()
    }
    state = if (isLayerActive) IN_LAYER_STATE else super.getState()
  }

  override fun getCurrentPosition(): LexerPosition = object : LexerPosition {
    override val offset: Int = this@LayeredLexer.getTokenStart()
    override val state: Int = this@LayeredLexer.getState()
  }

  private fun Lexer.isStopToken(tokenType: SyntaxElementType?): Boolean {
    val stopTokens = stopTokensMap[this] ?: return false
    return tokenType in stopTokens
  }

  protected val isLayerActive: Boolean get() = currentLayerLexer != null
  private val isInLayerEndGap get() : Boolean = layerLeftPart != -1

  companion object {
    const val IN_LAYER_STATE: Int = 1024
    const val IN_LAYER_LEXER_FINISHED_STATE: Int = 2048

    @JvmField
    val DISABLE_LAYERS_FLAG: ThreadLocalKmp<Boolean?> = ThreadLocalKmp()
  }
}
