/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.util.concurrency

class RejectedPromise<T>(private val error: Throwable) : Promise<T> {
  override public val state: Promise.State
    get() = Promise.State.REJECTED

  override fun done(done: (T) -> Unit) = this

  override fun processed(fulfilled: AsyncPromise<T>): Promise<T> {
    fulfilled.setError(error)
    return this
  }

  override fun rejected(rejected: (Throwable) -> Unit): Promise<T> {
    if (!rejected.isObsolete()) {
      rejected(error)
    }
    return this
  }

  override fun processed(processed: (T) -> Unit): RejectedPromise<T> {
    processed(null)
    return this
  }

  override fun <SUB_RESULT> then(done: (T) -> SUB_RESULT): Promise<SUB_RESULT> {
    @suppress("UNCHECKED_CAST")
    return this as Promise<SUB_RESULT>
  }

  override fun notify(child: AsyncPromise<T>) {
    child.setError(error)
  }
}