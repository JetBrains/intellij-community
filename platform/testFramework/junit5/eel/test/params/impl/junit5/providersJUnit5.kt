// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.impl.junit5

import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolderImpl
import com.intellij.platform.testFramework.junit5.eel.params.api.LocalEelHolder
import com.intellij.platform.testFramework.junit5.eel.params.impl.providers.getIjentTestProviders
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.platform.commons.util.AnnotationUtils
import org.junitpioneer.jupiter.cartesian.CartesianParameterArgumentsProvider
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.stream.Stream

@TestOnly
internal class EelArgumentsProvider : ArgumentsProvider {
  override fun provideArguments(context: ExtensionContext): Stream<out Arguments> =
    getProvidersAsStream(context).map(Arguments::of)
}

@TestOnly
internal class EelCartesianArgumentsProvider : CartesianParameterArgumentsProvider<EelHolder> {
  override fun provideArguments(
    context: ExtensionContext,
    parameter: Parameter,
  ): Stream<EelHolder> = getProvidersAsStream(context)
}

@TestOnly
private fun getProvidersAsStream(context: ExtensionContext): Stream<EelHolder> {
  val providers: Array<EelHolder> = arrayOf<EelHolder>(LocalEelHolder) + getIjentTestProviders().flatMap { provider ->
    createHolders(provider, context.testMethod.get())
  }.toTypedArray<EelHolder>()
  return Stream.of(*providers)
}

private fun <T : Annotation> createHolders(provider: EelIjentTestProvider<T>, testMethod: Method): List<EelHolderImpl<T>> {
  val annotations =
    AnnotationUtils.findRepeatableAnnotations(testMethod, provider.mandatoryAnnotationClass.java)
  return if (annotations.isEmpty()) {
    listOf(EelHolderImpl(provider, null))
  }
  else {
    annotations.map { EelHolderImpl(provider, it) }
  }
}


