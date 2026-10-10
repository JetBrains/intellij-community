// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Unmodifiable
import java.util.Collections
import java.util.function.Function
import java.util.function.Supplier

/**
 * Values of [ModPlanOption]s for one ModPlan refactoring. Only the values set explicitly are stored, by option id;
 * every other option yields its default. A value of a caller is stored as it came, and a value that the option does not accept is
 * ignored when the option is read, as an unknown id is.
 *
 * **Immutable.** Nothing changes an instance: [with] and [withAnswers] return a copy, so an instance can be shared, kept
 * across a question and compared.
 */
class ModPlanOptionValues private constructor(values: Map<String, Any?>) {
  private val myValues: @Unmodifiable Map<String, Any?> = Collections.unmodifiableMap(values)

  /**
   * @return the explicit value of [option], or its default; a value that is neither an instance of the type of [option] nor
   * the name of a constant of an enum option is ignored, as an unknown id is
   */
  fun <T : Any> get(option: ModPlanOption<T>): T = option.fromRaw(myValues[option.id]) ?: option.defaultValue

  /**
   * @return whether [option] has an explicit value, such as an answer of the run
   */
  fun has(option: ModPlanOption<*>): Boolean = myValues.containsKey(option.id)

  /** @return a copy of these options in which [option] has the given value */
  fun <T : Any> with(option: ModPlanOption<T>, value: T): ModPlanOptionValues {
    val values = LinkedHashMap(myValues)
    values[option.id] = value
    return ModPlanOptionValues(values)
  }

  /** @return the chosen keys of [option], in their order */
  fun chosenKeys(option: ModPlanOption<String>): @Unmodifiable Set<String> {
    return Collections.unmodifiableSet(keysOf(option).filterTo(LinkedHashSet()) { !it.startsWith(NOT_CHOSEN) })
  }

  /** @return the keys of [option] that are answered: the chosen ones, and the ones that were asked about and not chosen */
  fun answeredKeys(option: ModPlanOption<String>): @Unmodifiable Set<String> {
    return Collections.unmodifiableSet(keysOf(option).mapTo(LinkedHashSet()) { it.removePrefix(NOT_CHOSEN) })
  }

  /**
   * The answers of a question about each element add up: a key chosen before stays chosen.
   *
   * @param askedKeys  the keys that the question asked about
   * @param chosenKeys the asked keys that were chosen
   * @return a copy of these options in which [option] holds [askedKeys]: the chosen ones, and the others as not chosen
   */
  fun withAnswers(option: ModPlanOption<String>, askedKeys: Collection<String>, chosenKeys: Collection<String>): ModPlanOptionValues {
    val allChosenKeys = LinkedHashSet(chosenKeys(option))
    allChosenKeys.addAll(chosenKeys)
    val lines = LinkedHashSet(allChosenKeys)
    for (key in answeredKeys(option)) {
      if (key !in allChosenKeys) lines.add(NOT_CHOSEN + key)
    }
    for (key in askedKeys) {
      if (key !in allChosenKeys) lines.add(NOT_CHOSEN + key)
    }
    return with(option, lines.joinToString("\n"))
  }

  private fun keysOf(option: ModPlanOption<String>): Set<String> {
    return StringUtil.split(get(option), "\n").map { it.trim() }.filterTo(LinkedHashSet()) { it.isNotEmpty() }
  }

  override fun equals(other: Any?): Boolean = this === other || other is ModPlanOptionValues && myValues == other.myValues

  override fun hashCode(): Int = myValues.hashCode()

  companion object {
    @JvmField
    val EMPTY: ModPlanOptionValues = ModPlanOptionValues(mapOf())

    /** The prefix of a key that was asked about and not chosen, see [withAnswers]. */
    private const val NOT_CHOSEN = "-"
  }
}

/**
 * A typed option of a ModPlan refactoring. Options are plain data: the language code reads them from [ModPlanOptionValues], and a
 * [ModPlanQuestion] asks for them. Two options are equal when their ids are equal.
 *
 * @param id           wire name for MCP/LSP and dialogs; must match `[a-z][A-Za-z0-9]*`
 * @param type         the class of the option value: [Boolean], [String] or an enum
 * @param defaultValue the value used when nobody sets the option; the code must stay consistent with it
 * @param description  lazy bundle lookup; may contain the '&amp;' mnemonic
 * @param valueText    the presentable text of a value, for an enum option; null for a boolean option
 */
class ModPlanOption<T : Any>(
  val id: @NonNls String,
  val type: Class<T>,
  val defaultValue: T,
  val description: Supplier<@NlsContexts.Checkbox String>,
  val valueText: Function<in T, @Nls String>?,
) {
  init {
    require(ID_PATTERN.matches(id)) { "Option id must match ${ID_PATTERN.pattern}: $id" }
  }

  /**
   * @param rawValue a value received from a caller, such as a JSON value: the name of a constant stands for the value of an enum option
   * @return [rawValue] as the value of this option; null if it is neither an instance of [type] nor the name of a constant
   */
  fun fromRaw(rawValue: Any?): T? {
    if (rawValue is String && type.isEnum) {
      return type.enumConstants.firstOrNull { (it as Enum<*>).name == rawValue }
    }
    return if (type.isInstance(rawValue)) type.cast(rawValue) else null
  }

  override fun equals(other: Any?): Boolean = this === other || other is ModPlanOption<*> && id == other.id

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String = id

  companion object {
    private val ID_PATTERN = Regex("[a-z][A-Za-z0-9]*")

    /** Creates a boolean option, see [id], [defaultValue] and [description]. */
    @JvmStatic
    fun bool(id: @NonNls String, defaultValue: Boolean, description: Supplier<@NlsContexts.Checkbox String>): ModPlanOption<Boolean> {
      return ModPlanOption(id, Boolean::class.javaObjectType, defaultValue, description, null)
    }

    /**
     * Creates an option with a text value, such as a list of names. Only a [ModPlanQuestion.Custom] question is expected to set it,
     * but a form shows it as an edit box. See [id], [defaultValue] and [description].
     */
    @JvmStatic
    fun text(id: @NonNls String, defaultValue: String, description: Supplier<@NlsContexts.Label String>): ModPlanOption<String> {
      return ModPlanOption(id, String::class.java, defaultValue, description, null)
    }

    /**
     * Creates an option with one of several values.
     *
     * @param enumClass the values of the option; it is [type]
     */
    @JvmStatic
    fun <E : Enum<E>> choice(
      id: @NonNls String,
      enumClass: Class<E>,
      defaultValue: E,
      description: Supplier<@NlsContexts.Label String>,
      valueText: Function<in E, @Nls String>,
    ): ModPlanOption<E> {
      return ModPlanOption(id, enumClass, defaultValue, description, valueText)
    }
  }
}