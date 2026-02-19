// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.KProperty

private const val argsFileKey = "@args-file"

private class Args(args: List<String>) {
  private val args = lazy {
    val finalArgs = mutableListOf<String>()
    finalArgs += args

    val argsFiles = ArgsReader(lazyOf(args), argsFileKey).args { File(it) }
    for (argsFile in argsFiles) {
      if (!argsFile.isFile) error("Failed to load parameters file from $argsFile")
      finalArgs += argsFile.readLines().filter { it.isNotBlank() }
    }
    finalArgs
  }

  fun forName(name: String) = ArgsReader(args, name)
}

private class ArgsReader(
  private val args: Lazy<List<String>>,
  name: String
) {
  private val key = "--$name="
  private val keyWithoutParameter = "--$name"

  inline fun <T> nonEmptyArgs(map: (String) -> T): List<T> {
    val result = args(map)
    if (result.isEmpty()) {
      throw IllegalArgumentException("argument $key should be specified at least once")
    }
    return result
  }

  inline fun <T> args(map: (String) -> T): List<T> =
    args.value
      .filter { it.startsWith(key) }
      .map { it.removePrefix(key) }
      .map { value ->
        try {
          map(value)
        }
        catch (e: Exception) {
          throw IllegalArgumentException("argument $key has incorrect value specified: $value: ${e.message}")
        }
      }

  inline fun <T : Any> arg(map: (String) -> T, noinline defaultF: (() -> T)?): T {
    val values = args(map)

    if (values.isEmpty()) {
      requireNotNull(defaultF) { "argument $key is not specified" }
      return defaultF()
    }

    require(values.size == 1) { "argument $key is specified multiple times: $values" }
    return values.first()
  }

  inline fun <T : Any> argWithoutValue(map: (String) -> T, noinline whenWithoutParameter: () -> T, noinline whenNotProvided: () -> T) : T {
    val withoutParam = args.value.contains(keyWithoutParameter)
    if (withoutParam) {
      return whenWithoutParameter()
    }
    return arg(map, whenNotProvided)
  }

  inline fun <T : Any> argOrNull(map: (String) -> T): T? {
    val values = args(map)
    require(values.size <= 1) { "argument $key is specified multiple times: $values" }
    return values.firstOrNull()
  }

  fun reportDeprecatedUsage(deprecatedMessage: String) {
    if (args { it }.isNotEmpty()) {
      logger<ArgsParser>().warn("Using of deprecated parameter: $key. $deprecatedMessage")
    }
  }
}

@ApiStatus.Internal
class ArgsParser(args: List<String>) {
  private val args = Args(args)
  private val arguments = mutableListOf<TypedArg<*>>()

  fun arg(name: String, description: String): ArgInfo = ArgInfo(name, description)

  init {
    arg(
      "@args-file",
      "Arguments file to avoid commandline overflow\n" +
      "Every line represents an unescaped argument"
    ).optional().hidden().files()
  }

  fun tryReadAll() {
    arguments.forEach { it.parseValue() }
  }

  fun usage(includeHidden: Boolean = false, commandPadding: Int = 4): String = buildString {
    val commandPad = " ".repeat(commandPadding)

    val argsToShow = arguments.sortedWith(
      compareBy(
        {
          var result = 0
          if (it.info.optional) result += 3
          if (it.info.hidden) result += 5
          if (it.info.deprecated != null) result += 10
          result
        },
        {
          it.info.name
        }
      )
    )
      .filter { includeHidden || !it.info.hidden }
      .map { it to (commandPad + "--${it.info.name}=<value>") }

    val descrPadSize = 2 + (argsToShow.map { it.second.length }.maxOrNull() ?: 0)
    val descrPad = " ".repeat(descrPadSize)

    for ((argument, argInfo) in argsToShow) {
      val deprecatedLine = argument.info.deprecated?.let {
        val lines = it.split("\n")
        listOf("DEPRECATED. ${lines.first()}") + lines.drop(1)
      } ?: listOf()

      val descriptionText = when {
                              argument.info.hidden -> "[hidden] "
                              argument.info.optional -> "[optional] "
                              else -> ""
                            } + argument.info.description.capitalize()
      val descriptionLines = deprecatedLine + descriptionText.split("\n")

      appendLine(argInfo.padEnd(descrPadSize) + (descriptionLines.firstOrNull() ?: ""))
      descriptionLines.drop(1).forEach { append(descrPad).appendLine(it.trim()) }
      appendLine()
    }
  }

  inner class ArgInfo(val name: String, val description: String) {
    var hidden: Boolean = false
    var optional: Boolean = false
    var deprecated: String? = null

    fun hidden(): ArgInfo = optional().apply { hidden = true }
    fun optional(): ArgInfo = apply { optional = true }
    fun deprecated(message: String): ArgInfo = apply { deprecated = message }

    fun string(default: (() -> String)? = null): TypedArg<String> = parse { arg({ it }, default) }
    fun strings(): TypedArg<List<String>> = parse { args { it } }
    fun notEmptyStrings(): TypedArg<List<String>> = parse { nonEmptyArgs { it } }
    fun stringOrNull(): TypedArg<String?> = optional().parse { argOrNull { it } }

    fun boolean(default: () -> Boolean = { false }): TypedArg<Boolean> = parse { arg(toBoolean, default) }
    fun booleanOrNull(): TypedArg<Boolean?> = optional().parse { argOrNull(toBoolean) }

    /**
     * Allows to parse boolean flags.
     *
     * If the command line arguments contain strings '--foo' or '--foo=true', then it sets the argument 'foo' to `true`.
     * If the command line arguments contain a string '--foo=false' or do not contain an argument which key is '--foo', then it
     * sets the argument 'foo' to `false`.
     */
    fun flag(): TypedArg<Boolean> = optional().parse { argWithoutValue(toBoolean, { true }, { false }) }

    fun int(default: () -> Int): TypedArg<Int> = parse { arg(toInt, default) }
    fun toIntOrNull(): TypedArg<Int?> = optional().parse { argOrNull(toInt) }

    fun file(default: (() -> Path)? = null): TypedArg<Path> = parse { arg(toFile, default) }
    fun fileOrNull(): TypedArg<Path?> = optional().parse { argOrNull(toFile) }

    fun files(): TypedArg<List<Path>> = parse { args(toFile) }

    fun notEmptyFiles(): TypedArg<List<Path>> = parse { nonEmptyArgs(toFile) }


    private val toBoolean: (String) -> Boolean = { string ->
      require(string == "true" || string == "false") { "wrong boolean value: $string" }
      string == "true"
    }

    private val toInt: (String) -> Int = { string -> string.toInt() }

    private val toFile: (String) -> Path = { Paths.get(it).toAbsolutePath() }

    private fun <T> parse(compute: ArgsReader.() -> T): TypedArg<T> {
      val deprecated = deprecated
      val arg = TypedArg(this) {
        val reader = args.forName(name)
        if (deprecated != null) {
          reader.reportDeprecatedUsage(deprecated)
        }
        reader.compute()
      }
      arguments += arg
      return arg
    }
  }

  class TypedArg<T>(val info: ArgInfo, private val computeValue: () -> T) {
    private var delegate : TypedArg<*>? = null
    private val paramValue = lazy { computeValue() }

    fun andApply(action: T.() -> Unit): TypedArg<T> = andMap { it.apply(action) }

    fun <Y> andMap(action: (T) -> Y) : TypedArg<Y> {
      require(!paramValue.isInitialized() && delegate == null) { "cannot apply transformations to the computed value: $this" }
      val newArgs = TypedArg(info) { action(getValue()) }
      //bind the computation to implement the parse function
      delegate = newArgs
      return newArgs
    }

    internal fun parseValue() {
      (delegate ?: this).getValue()
    }

    fun getValue(): T = paramValue.value

    operator fun getValue(thisRef: Any?, property: KProperty<*>?): T = getValue()
  }
}
