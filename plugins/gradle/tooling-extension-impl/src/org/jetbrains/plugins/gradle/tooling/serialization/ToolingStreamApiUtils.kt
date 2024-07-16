// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization

import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.Supplier

fun IonWriter.step(containerType: IonType, writeModel: Runnable) {
  stepIn(containerType)
  writeModel.run()
  stepOut()
}

fun <R> IonReader.step(readModel: Supplier<R>): R {
  stepIn()
  val result = readModel.get()
  stepOut()
  return result
}