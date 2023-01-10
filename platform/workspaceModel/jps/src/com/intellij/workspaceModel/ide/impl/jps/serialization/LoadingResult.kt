// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jdom.JDOMException
import java.io.IOException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class LoadingResult<T>(
  val data: T,
  val exception: Throwable? = null,
)

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
