// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

sealed class GenericNotAvailableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
open class NotEnoughInformationCause(message: String, cause: NotEnoughInformationCause? = null) : GenericNotAvailableException(message, cause) {
  override fun toString(): String = localizedMessage
}
object UnspecifiedNotAvailableException : NotEnoughInformationCause("property value is not available") // TODO delete and fix usages
open class VfsRecoveryException(message: String? = null, cause: Throwable? = null) : GenericNotAvailableException(message, cause)