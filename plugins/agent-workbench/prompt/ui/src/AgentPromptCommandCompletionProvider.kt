// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceKind
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionPromptCommandCompletionEntry
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionPromptCommandCompletionKind
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.util.TextFieldCompletionProviderDumbAware

private const val CODEX_SKILL_PREFIX = '$'

private val CODEX_AGENT_SESSION_PROVIDER: AgentSessionProvider = AgentSessionProvider.from("codex")

internal class AgentPromptCommandCompletionProvider(
  private val selectedProvider: () -> AgentSessionProviderDescriptor?,
  private val resolveWorkingProjectPaths: () -> List<String>,
  private val resolveCodexSkillEntries: () -> List<AgentPromptReusableSourceEntry> = { emptyList() },
) : TextFieldCompletionProviderDumbAware() {
  override fun getPrefix(text: String, offset: Int): String? {
    val provider = selectedProvider()
    val slashPrefix = findPromptCommandCompletionPrefix(text, offset)
    if (slashPrefix != null && provider?.hasPromptCommandCompletion() == true) {
      return slashPrefix
    }
    if (provider?.provider == CODEX_AGENT_SESSION_PROVIDER) {
      return findCodexSkillCompletionPrefix(text, offset)
    }
    return null
  }

  override fun acceptChar(c: Char): CharFilter.Result? {
    return if (c == '/' || c == '$' || c == '-' || c == '_' || c.isLetterOrDigit()) CharFilter.Result.ADD_TO_PREFIX else null
  }

  override fun applyPrefixMatcher(result: CompletionResultSet, prefix: String): CompletionResultSet {
    return result.withPrefixMatcher(PlainPrefixMatcher(prefix, true)).caseInsensitive()
  }

  override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
    val provider = selectedProvider()
    when {
      prefix.startsWith('/') && provider != null -> provider.collectPromptCommandCompletionEntries(resolveWorkingProjectPaths()).forEach { entry ->
        result.addElement(entry.toLookupElement())
      }
      prefix.startsWith(CODEX_SKILL_PREFIX) && provider?.provider == CODEX_AGENT_SESSION_PROVIDER -> resolveCodexSkillEntries()
        .asSequence()
        .filter { entry -> entry.kind == AgentPromptReusableSourceKind.SKILL }
        .forEach { entry -> result.addElement(entry.toLookupElement()) }
    }
  }
}

internal fun findPromptCommandCompletionPrefix(text: CharSequence, offset: Int): String? {
  val safeOffset = offset.coerceIn(0, text.length)
  val tokenStart = findCompletionTokenStart(text, safeOffset)
  if (tokenStart >= safeOffset) {
    return null
  }

  val prefix = text.subSequence(tokenStart, safeOffset).toString()
  return prefix.takeIf { candidate -> candidate.startsWith('/') }
}

internal fun findCodexSkillCompletionPrefix(text: CharSequence, offset: Int): String? {
  val safeOffset = offset.coerceIn(0, text.length)
  val tokenStart = findCompletionTokenStart(text, safeOffset)
  if (tokenStart >= safeOffset) {
    return null
  }

  val prefix = text.subSequence(tokenStart, safeOffset).toString()
  return prefix.takeIf { candidate -> candidate.startsWith('$') }
}

private fun findCompletionTokenStart(text: CharSequence, offset: Int): Int {
  var index = offset - 1
  while (index >= 0) {
    when (text[index]) {
      ' ', '\n', '\t', '\r' -> return index + 1
    }
    index--
  }
  return 0
}

internal fun shouldAutoPopupPromptCommandCompletion(
  selectedProvider: AgentSessionProviderDescriptor?,
  workingProjectPaths: Iterable<String?>,
  text: CharSequence,
  offsetAfterChange: Int,
  insertedFragment: CharSequence,
): Boolean {
  if (selectedProvider == null) {
    return false
  }
  if (insertedFragment.length != 1 || insertedFragment[0] != '/') {
    return false
  }
  if (offsetAfterChange != 1 || text.isEmpty() || text[0] != '/') {
    return false
  }
  if (findPromptCommandCompletionPrefix(text, offsetAfterChange) != "/") {
    return false
  }
  return selectedProvider.collectPromptCommandCompletionEntries(workingProjectPaths).isNotEmpty()
}

internal fun shouldAutoPopupCodexSkillCompletion(
  selectedProvider: AgentSessionProvider?,
  text: CharSequence,
  offsetAfterChange: Int,
  insertedFragment: CharSequence,
): Boolean {
  if (selectedProvider != CODEX_AGENT_SESSION_PROVIDER) {
    return false
  }
  if (insertedFragment.length != 1 || insertedFragment[0] != '$') {
    return false
  }
  if (offsetAfterChange != 1 || text.isEmpty() || text[0] != '$') {
    return false
  }
  return findCodexSkillCompletionPrefix(text, offsetAfterChange) == CODEX_SKILL_PREFIX.toString()
}

private fun AgentSessionProviderDescriptor.hasPromptCommandCompletion(): Boolean {
  return collectPromptCommandCompletionEntries(emptyList()).isNotEmpty()
}

private fun AgentSessionPromptCommandCompletionEntry.toLookupElement(): LookupElement {
  val lookupString = command.trim()
  val builder = LookupElementBuilder.create(this, lookupString)
    .withPresentableText(lookupString)
    .withTypeText(kind.label, true)

  return (argumentHint.takeIf(String::isNotBlank)?.let { hint ->
    builder.withTailText(" $hint", true)
  } ?: builder).withInsertHandler { context, _ ->
    val tailOffset = context.tailOffset
    val chars = context.document.charsSequence
    if (tailOffset < chars.length && chars[tailOffset].isWhitespace()) {
      return@withInsertHandler
    }
    if (tailOffset == chars.length) {
      context.document.insertString(tailOffset, " ")
      context.tailOffset = tailOffset + 1
    }
  }
}

private val AgentSessionPromptCommandCompletionKind.label: String
  get() = when (this) {
    AgentSessionPromptCommandCompletionKind.MENU -> AgentPromptBundle.message("popup.completion.type.menu")
    AgentSessionPromptCommandCompletionKind.COMMAND -> AgentPromptBundle.message("popup.completion.type.command")
    AgentSessionPromptCommandCompletionKind.SKILL -> AgentPromptBundle.message("popup.completion.type.skill")
  }

private fun AgentPromptReusableSourceEntry.toLookupElement(): LookupElement {
  val lookupString = insertText.trim()
  val builder = LookupElementBuilder.create(this, lookupString)
    .withPresentableText(lookupString)
    .withTypeText(AgentPromptBundle.message("popup.completion.type.skill"), true)

  return (description?.takeIf(String::isNotBlank)?.let { hint ->
    builder.withTailText(" $hint", true)
  } ?: builder).withInsertHandler { context, _ ->
    val tailOffset = context.tailOffset
    val chars = context.document.charsSequence
    if (tailOffset < chars.length && chars[tailOffset].isWhitespace()) {
      return@withInsertHandler
    }
    if (tailOffset == chars.length) {
      context.document.insertString(tailOffset, " ")
      context.tailOffset = tailOffset + 1
    }
  }
}
