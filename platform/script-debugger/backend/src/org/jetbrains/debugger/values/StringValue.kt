package org.jetbrains.debugger.values

import org.jetbrains.concurrency.Promise

interface StringValue : Value {
  val isTruncated: Boolean

  val length: Int

  /**
   * Asynchronously reloads object value with extended size limit
   */
  val fullString: Promise<String>
}