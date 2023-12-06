// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.timemachine

open class NotAvailableException(message: String, cause: Throwable? = null) : Exception(message, cause, false, false) {
  override fun toString(): String = localizedMessage
}
open class NotEnoughInformationCause(message: String, cause: Throwable? = null) : NotAvailableException(message, cause) {
  override fun toString(): String = localizedMessage
}
object UnspecifiedNotAvailableException : NotEnoughInformationCause("property value is not available") // TODO delete and fix usages
open class VfsRecoveryException(message: String, cause: Throwable? = null) : NotAvailableException(message, cause)