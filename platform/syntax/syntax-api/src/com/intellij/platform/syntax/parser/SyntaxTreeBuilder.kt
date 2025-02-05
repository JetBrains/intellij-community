// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.TokenList
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * An API for building a syntax tree from a text.
 * The result of parsing can be obtained by calling [com.intellij.platform.syntax.ProductionResultKt.prepareProduction] function.
 *
 * @See SyntaxTreeBuilderFactory
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface SyntaxTreeBuilder {
  /**
   * Returns the complete text being parsed.
   *
   * @return the text being parsed
   */
  val text: CharSequence

  /**
   * Advances the lexer to the next token, skipping whitespace and comment tokens.
   */
  fun advanceLexer()

  /**
   * Returns the type of current token from the lexer.
   *
   * @return the token type, or `null` when the token stream is over.
   * @see .setTokenTypeRemapper
   */
  val tokenType: SyntaxElementType?

  /**
   * Sets optional remapper that can change the type of tokens.
   * Output of [.getTokenType] is affected by it.
   *
   * @param remapper the remapper object, or `null`.
   */
  fun setTokenTypeRemapper(remapper: SyntaxElementTypeRemapper?)

  /**
   * Slightly easier way to what [SyntaxElementTypeRemapper] does (i.e. it just remaps current token to a given type).
   *
   * @param type new type for the current token.
   */
  fun remapCurrentToken(type: SyntaxElementType)

  /**
   * Subscribe for notification on default whitespace and comments skipped events.
   *
   * @param callback an implementation for the callback
   */
  fun setWhitespaceSkippedCallback(callback: WhitespaceSkippedCallback?)

  /**
   * See what token type is in `steps` ahead.
   *
   * @param steps 0 is current token (i.e., the same [PsiBuilder.getTokenType] returns)
   * @return type element which [.getTokenType] will return if we call advance `steps` times in a row
   */
  fun lookAhead(steps: Int): SyntaxElementType?

  /**
   * See what token type is in `steps` ahead/behind.
   *
   * @param steps 0 is current token (i.e., the same [PsiBuilder.getTokenType] returns)
   * @return type element ahead or behind, including whitespace/comment tokens
   */
  fun rawLookup(steps: Int): SyntaxElementType?

  /**
   * See what token type is in `steps` ahead/behind current position.
   *
   * @param steps 0 is current token (i.e., the same [.getTokenType] returns)
   * @return offset type element ahead or behind, including whitespace/comment tokens, -1 if first token,
   * `getOriginalText().getLength()` at end
   */
  fun rawTokenTypeStart(steps: Int): Int

  /**
   * Returns the index of the current token in the original sequence.
   *
   * @return token index
   */
  fun rawTokenIndex(): Int

  /**
   * Returns the text of the current token from the lexer.
   *
   * @return the token text, or `null` when the token stream is over.
   */
  val tokenText: @NonNls String?

  /**
   * Advance lexer by `steps` tokens (including whitespace or comments tokens) ahead of current position.
   * Afterward, any whitespace or comment tokens will be skipped. This method used together with [.rawLookup] may
   * bring performance benefits when collapsing large code blocks with
   * [com.intellij.psi.tree.IReparseableElementType] tokens.
   * <br></br><br></br>
   * The default implementation does not bring any performance benefits over [.advanceLexer] method and should be overridden.
   *
   * @param steps a positive integer
   */
  fun rawAdvanceLexer(steps: Int) {
    require(steps >= 0) {
      "Steps must be a positive integer - lexer can only be advanced. " +
      "Use Marker.rollbackTo if you want to rollback PSI building."
    }
    if (steps == 0) return
    val offset = rawTokenTypeStart(steps)
    while (!eof() && this.currentOffset < offset) {
      advanceLexer()
    }
  }

  /**
   * Returns the start offset of the current token, or the file length when the token stream is over.
   *
   * @return the token offset.
   */
  val currentOffset: Int

  /**
   * Creates a marker at the current parsing position.
   *
   * @return the new marker instance.
   */
  fun mark(): Marker

  /**
   * Adds an error marker with the specified message text at the current position in the tree.
   * <br></br>**Note**: from series of subsequent errors messages only first will be part of resulting tree.
   *
   * @param messageText the text of the error message displayed to the user.
   */
  fun error(messageText: @Nls String)

  /**
   * Checks if the lexer has reached the end of file.
   *
   * @return `true` if the lexer is at end of file, `false` otherwise.
   */
  fun eof(): Boolean

  /**
   * Enables or disables the builder debug mode. In debug mode, the builder will print stack trace
   * to marker allocation position if one is not done when calling [PsiBuilder.getTreeBuilt].
   *
   * @param dbgMode the debug mode value.
   */
  fun setDebugMode(dbgMode: Boolean)

  fun enforceCommentTokens(tokens: Set<SyntaxElementType>)

  /**
   * @return latest left done node for context dependent parsing.
   */
  val lastDoneMarker: Marker?

  /**
   * The list of currently existing production markers
   */
  val productions: List<Production>

  // todo extract to separate interface???
  /**
   * A view of token list being used for parsing.
   * The lexemes can mutate during parsing.
   */
  val lexemes: TokenList

  fun isWhitespaceOrComment(elementType: SyntaxElementType): Boolean = false

  fun hasErrorsAfter(marker: Marker): Boolean

  interface Production {
    fun getTokenType(): SyntaxElementType

    fun getStartOffset(): Int

    fun getEndOffset(): Int

    fun getStartIndex(): Int

    fun getEndIndex(): Int

    fun getErrorMessage(): @Nls String?

    fun isCollapsed(): Boolean

    // TODO invent a better name/way to distinguish CompositeMarker and ErrorLeaf markers.
    //      Note that CompositeMarker can have error as its element type, so checking the element type is unreliable.
    fun isErrorMarker(): Boolean
  }

  /**
   * A marker defines a range in the document text which becomes a node in the AST
   * tree. The ranges defined by markers within the text range of the current marker
   * become child nodes of the node defined by the current marker.
   */
  interface Marker : Production {
    // TODO remove Production from super types. Marker should not implement Production because most of Production methods don't make
    //      any sense until the marker isDone.
    //      Instead, done(...) method should return DoneMarker implementing Production.

    /**
     * Creates and returns a new marker starting immediately before the start of
     * this marker and extending after its end. Can be called on a completed or
     * a currently active marker.
     *
     * @return the new marker instance.
     */
    fun precede(): Marker

    /**
     * Drops this marker. Can be called after other markers have been added and completed
     * after this marker. Does not affect lexer position or markers added after this marker.
     */
    fun drop()

    /**
     * Drops this marker and all markers added after it, and reverts the lexer position to the
     * position of this marker.
     */
    fun rollbackTo()

    /**
     * Completes this marker and labels it with the specified AST node type. Before calling this method,
     * all markers added after the beginning of this marker must be either dropped or completed.
     *
     * @param type the type of the node in the AST tree.
     */
    fun done(type: SyntaxElementType)

    /**
     * Like [.done], but collapses all tokens between start and end markers
     * into single leaf node of given type.
     *
     * @param type the type of the node in the AST tree.
     */
    fun collapse(type: SyntaxElementType)

    /**
     * Like [.done], but the marker is completed (end marker inserted)
     * before specified one. All markers added between start of this marker and the marker specified as end one
     * must be either dropped or completed.
     *
     * @param type   the type of the node in the AST tree.
     * @param before marker to complete this one before.
     */
    fun doneBefore(type: SyntaxElementType, before: Marker)

    /**
     * Like [.doneBefore], but in addition an error element with given text
     * is inserted right before this marker's end.
     *
     * @param type         the type of the node in the AST tree.
     * @param before       marker to complete this one before.
     * @param errorMessage for error element.
     */
    fun doneBefore(type: SyntaxElementType, before: Marker, errorMessage: @Nls String)

    /**
     * Completes this marker and labels it as error element with specified message. Before calling this method,
     * all markers added after the beginning of this marker must be either dropped or completed.
     *
     * @param message for error element.
     */
    fun error(message: @Nls String)

    /**
     * Like [.error], but the marker is completed before specified one.
     *
     * @param message for error element.
     * @param before  marker to complete this one before.
     */
    fun errorBefore(message: @Nls String, before: Marker)

    /**
     * Allows to define custom edge token binders instead of default ones. If any of parameters is null
     * then corresponding token binder won't be changed (keeping previously set or default token binder).
     * It is an error to set right token binder for not-done marker.
     *
     * @param left  new left edge token binder.
     * @param right new right edge token binder.
     */
    fun setCustomEdgeTokenBinders(left: WhitespacesAndCommentsBinder?, right: WhitespacesAndCommentsBinder?)
  }
}
