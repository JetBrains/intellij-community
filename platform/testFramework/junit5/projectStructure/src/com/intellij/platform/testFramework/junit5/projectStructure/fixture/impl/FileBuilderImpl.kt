// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

internal sealed class FileBuilderImpl(
  val name: String,
)

internal class FileBuilderImplWithString(
  name: String,
  val content: String,
) : FileBuilderImpl(name)

internal class FileBuilderImplWithByteArray(
  name: String,
  val content: ByteArray,
) : FileBuilderImpl(name)