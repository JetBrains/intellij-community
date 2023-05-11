// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.text.OrdinalFormat
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.text.MessageFormat
import java.util.*
import java.util.function.BiConsumer

private val LOG: Logger
  get() = logger<BundleBase>()

private var assertOnMissedKeys = false
private val SUFFIXES = arrayOf("</body></html>", "</html>")

@Volatile
private var translationConsumer: BiConsumer<String, String>? = null

object BundleBase {
  const val MNEMONIC: Char = 0x1B.toChar()
  const val MNEMONIC_STRING: @NlsSafe String = MNEMONIC.toString()
  @JvmField
  val SHOW_LOCALIZED_MESSAGES: Boolean = java.lang.Boolean.getBoolean("idea.l10n")
  @JvmField
  val SHOW_DEFAULT_MESSAGES: Boolean = java.lang.Boolean.getBoolean("idea.l10n.english")
  @JvmField
  val SHOW_KEYS: Boolean = java.lang.Boolean.getBoolean("idea.l10n.keys")
  const val L10N_MARKER: String = "🔅"

  fun assertOnMissedKeys(doAssert: Boolean) {
    assertOnMissedKeys = doAssert
  }

  /**
   * Performs partial application of the pattern message from the bundle leaving some parameters unassigned.
   * It's expected that the message contains `params.length + unassignedParams` placeholders. Parameters
   * `{0}..{params.length-1}` will be substituted using a passed params array. The remaining parameters
   * will be renumbered: `{params.length}` will become `{0}` and so on, so the resulting template
   * could be applied once more.
   *
   * @param bundle resource bundle to find the message in
   * @param key resource key
   * @param unassignedParams number of unassigned parameters
   * @param params assigned parameters
   * @return a template suitable to pass to [MessageFormat.format] having the specified number of placeholders left
   */
  @JvmStatic
  fun partialMessage(bundle: ResourceBundle, key: String, unassignedParams: Int, params: Array<Any>): @Nls String {
    require(unassignedParams > 0)

    val newParams = params.copyOf(params.size + unassignedParams)
    val prefix = "#$$\$TemplateParameter$$$#"
    val suffix = "#$$$/TemplateParameter$$$#"
    for (i in 0 until unassignedParams) {
      newParams[i + params.size] = prefix + i + suffix
    }
    @Suppress("UNCHECKED_CAST")
    val message = message(bundle = bundle, key = key, params = newParams as Array<Any>)
    return quotePattern(message).replace(prefix, "{").replace(suffix, "}")
  }

  @JvmStatic
  fun message(bundle: ResourceBundle, key: String, vararg params: Any): @Nls String {
    return messageOrDefault(bundle = bundle, key = key, defaultValue = null, params = params)!!
  }

  @JvmStatic
  fun messageOrDefault(bundle: ResourceBundle?, key: String, defaultValue: @Nls String?, vararg params: Any?): @Nls String {
    if (bundle == null) {
      return defaultValue!!
    }

    var resourceFound = true
    val value = try {
      bundle.getString(key)
    }
    catch (e: MissingResourceException) {
      resourceFound = false
      useDefaultValue(bundle = bundle, key = key, defaultValue = defaultValue)
    }

    val result = postprocessValue(bundle = bundle, value = value, params = params)
    translationConsumer?.accept(key, result)
    return when {
      !resourceFound -> result
      SHOW_KEYS && SHOW_DEFAULT_MESSAGES -> {
        appendLocalizationSuffix(result = result, suffixToAppend = " ($key=${getDefaultMessage(bundle, key)})")
      }
      SHOW_KEYS -> appendLocalizationSuffix(result = result, suffixToAppend = " ($key)")
      SHOW_DEFAULT_MESSAGES -> {
        appendLocalizationSuffix(result = result, suffixToAppend = " (${getDefaultMessage(bundle, key)})")
      }
      SHOW_LOCALIZED_MESSAGES -> appendLocalizationSuffix(result = result, suffixToAppend = L10N_MARKER)
      else -> result
    }
  }

  @JvmStatic
  fun getDefaultMessage(bundle: ResourceBundle, key: String): String {
    try {
      val field = ResourceBundle::class.java.getDeclaredField("parent")
      field.isAccessible = true
      val parentBundle = field.get(bundle)
      if (parentBundle is ResourceBundle) {
        return parentBundle.getString(key)
      }
    }
    catch (e: IllegalAccessException) {
      LOG.warn("Cannot fetch default message with 'idea.l10n.english' enabled, by key '$key'")
    }
    return "undefined"
  }

  @JvmStatic
  fun appendLocalizationSuffix(result: String, suffixToAppend: String): @NlsSafe String {
    for (suffix in SUFFIXES) {
      if (result.endsWith(suffix)) {
        return result.substring(0, result.length - suffix.length) + L10N_MARKER + suffix
      }
    }
    return result + suffixToAppend
  }

  @JvmStatic
  @Suppress("HardCodedStringLiteral")
  fun useDefaultValue(bundle: ResourceBundle?, key: String, defaultValue: @Nls String?): @Nls String {
    if (defaultValue != null) {
      return defaultValue
    }
    if (assertOnMissedKeys) {
      val bundleName = if (bundle == null) "" else "(${bundle.baseBundleName})"
      LOG.error("'$key' is not found in $bundle$bundleName")
    }
    return "!$key!"
  }

  @JvmStatic
  @Suppress("HardCodedStringLiteral")
  fun postprocessValue(bundle: ResourceBundle, value: @Nls String, vararg params: Any?): @Nls String {
    @Suppress("NAME_SHADOWING") var value = value
    value = replaceMnemonicAmpersand(value)!!
    if (params.isNotEmpty() && value.contains('{')) {
      val locale = bundle.locale
      value = try {
        val format = if (locale == null) MessageFormat(value) else MessageFormat(value, locale)
        OrdinalFormat.apply(format)
        format.format(params)
      }
      catch (e: IllegalArgumentException) {
        "!invalid format: `$value`!"
      }
    }
    return value
  }

  @JvmStatic
  @Contract(pure = true)
  fun format(value: String, vararg params: Any): String {
    return if (params.isNotEmpty() && value.indexOf('{') >= 0) MessageFormat.format(value, *params) else value
  }

  @JvmStatic
  @Contract("null -> null; !null -> !null")
  fun replaceMnemonicAmpersand(value: @Nls String?): @Nls String? {
    if (value == null || !value.contains('&') || value.contains(MNEMONIC)) {
      return value
    }

    val builder = StringBuilder()
    val macMnemonic = value.contains("&&")
    var mnemonicAdded = false
    var i = 0
    while (i < value.length) {
      when (val c = value[i]) {
        '\\' -> {
          if (i < value.length - 1 && value[i + 1] == '&') {
            builder.append('&')
            i++
          }
          else {
            builder.append(c)
          }
        }
        '&' -> {
          if (i < value.length - 1 && value[i + 1] == '&') {
            if (SystemInfoRt.isMac) {
              if (!mnemonicAdded) {
                mnemonicAdded = true
                builder.append(MNEMONIC)
              }
            }
            i++
          }
          else if (!SystemInfoRt.isMac || !macMnemonic) {
            if (!mnemonicAdded) {
              mnemonicAdded = true
              builder.append(MNEMONIC)
            }
          }
        }
        else -> {
          builder.append(c)
        }
      }
      i++
    }
    @Suppress("HardCodedStringLiteral")
    return builder.toString()
  }

  /** The consumer is used by the "robot-server" plugin to collect key/text pairs - handy for writing UI tests for different locales.  */
  @Suppress("unused")
  @TestOnly
  @JvmStatic
  fun setTranslationConsumer(consumer: BiConsumer<String, String>?) {
    translationConsumer = consumer
  }
}

private fun quotePattern(message: String): @NlsSafe String {
  var inQuotes = false
  val sb = StringBuilder(message.length + 5)
  for (c in message) {
    val needToQuote = c == '{' || c == '}'
    if (needToQuote != inQuotes) {
      inQuotes = needToQuote
      sb.append('\'')
    }
    if (c == '\'') {
      sb.append("''")
    }
    else {
      sb.append(c)
    }
  }
  if (inQuotes) {
    sb.append('\'')
  }
  return sb.toString()
}