// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.annotations.processors

import org.jetbrains.plugins.gradle.testFramework.annotations.ArgumentsProcessor
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream

abstract class DelegateArgumentsProcessor<T : Annotation, D : Annotation> : ArgumentsProcessor<T> {

  private val argumentsProcessor by lazy { createArgumentsProcessor() }

  abstract fun createArgumentsProcessor(): ArgumentsProcessor<D>

  abstract fun convertAnnotation(annotation: T): D

  open fun convertArguments(arguments: Arguments, context: ExtensionContext): Arguments = arguments

  open fun filterArguments(arguments: Arguments, context: ExtensionContext): Boolean = true

  override fun accept(annotation: T) {
    argumentsProcessor.accept(convertAnnotation(annotation))
  }

  final override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
    return argumentsProcessor.provideArguments(context)
      .map { convertArguments(it, context) }
      .filter { filterArguments(it, context) }
  }
}
