// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.impl.junit5

import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.impl.providers.getEelTestProviders
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junitpioneer.jupiter.cartesian.CartesianParameterArgumentsProvider
import java.lang.reflect.Parameter
import java.util.stream.Stream

@TestOnly
internal class EelArgumentsProvider : ArgumentsProvider {
  override fun provideArguments(context: ExtensionContext): Stream<out Arguments> =
    getProvidersAsStream().map(Arguments::of)
}

@TestOnly
internal class EelCartesianArgumentsProvider : CartesianParameterArgumentsProvider<EelHolder> {
  override fun provideArguments(
    context: ExtensionContext,
    parameter: Parameter,
  ): Stream<EelHolder> = getProvidersAsStream()
}

@TestOnly
private fun getProvidersAsStream(): Stream<EelHolder> {
  val providers = getEelTestProviders().map {
    EelHolderImpl(it)
  }.toTypedArray()
  return Stream.of(*providers)
}


