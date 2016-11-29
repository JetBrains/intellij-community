/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.concurrency

import com.intellij.util.Consumer
import com.intellij.util.Function

import java.util.concurrent.TimeUnit

internal class RejectedPromise<T>(private val error: Throwable) : Promise<T> {
  override fun getState() = Promise.State.REJECTED

  override fun done(done: Consumer<in T>) = this

  override fun processed(fulfilled: AsyncPromise<in T>): Promise<T> {
    fulfilled.setError(error)
    return this
  }

  override fun rejected(rejected: Consumer<Throwable>): Promise<T> {
    if (!isObsolete(rejected)) {
      rejected.consume(error)
    }
    return this
  }

  override fun processed(processed: Consumer<in T>): RejectedPromise<T> {
    processed.consume(null)
    return this
  }

  @Suppress("UNCHECKED_CAST")
  override fun <SUB_RESULT> then(handler: Function<in T, out SUB_RESULT>) = this as Promise<SUB_RESULT>

  @Suppress("UNCHECKED_CAST")
  override fun <SUB_RESULT> thenAsync(handler: Function<in T, Promise<SUB_RESULT>>) = this as Promise<SUB_RESULT>

  override fun notify(child: AsyncPromise<in T>) {
    child.setError(error)
  }

  override fun blockingGet(timeout: Int, timeUnit: TimeUnit): T? {
    throw error
  }
}