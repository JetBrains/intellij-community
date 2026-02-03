// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import org.jdom.JDOMException
import java.io.IOException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class LoadingResult<T>(
  val data: T,
  val exception: Throwable? = null,
)

/**
 * Receiver is used to reduce the visibility of the function
 */
@Suppress("UnusedReceiverParameter")
@OptIn(ExperimentalContracts::class)
inline fun <T> JpsFileEntitiesSerializer<*>.runCatchingXmlIssues(body: () -> T): Result<T> {
  contract {
    callsInPlace(body, InvocationKind.EXACTLY_ONCE)
  }
  return try {
    Result.success(body())
  }
  catch (e: JDOMException) {
    Result.failure(e)
  }
  catch (e: IOException) {
    Result.failure(e)
  }
}

@Suppress("UnusedReceiverParameter")
@OptIn(ExperimentalContracts::class)
inline fun <T> JpsFileEntitiesSerializer<*>.runCatchingXmlIssues(exceptionsCollector: MutableCollection<Throwable>, body: () -> T): T? {
  contract {
    callsInPlace(body, InvocationKind.EXACTLY_ONCE)
  }
  try {
    return body()
  }
  catch (e: JDOMException) {
    exceptionsCollector.add(e)
  }
  catch (e: IOException) {
    exceptionsCollector.add(e)
  }
  return null
}
