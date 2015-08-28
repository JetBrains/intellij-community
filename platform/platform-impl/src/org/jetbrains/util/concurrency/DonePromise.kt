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

import org.jetbrains.concurrency.Obsolescent

private class DonePromise<T>(private val result: T) : Promise<T> {
  override val state: Promise.State
    get() = Promise.State.FULFILLED

  override fun done(done: (T) -> Unit): Promise<T> {
    if (!done.isObsolete()) {
      done(result)
    }
    return this
  }

  override fun processed(fulfilled: AsyncPromise<T>): Promise<T> {
    fulfilled.setResult(result)
    return this
  }

  override fun processed(processed: (T) -> Unit): Promise<T> {
    done(processed)
    return this
  }

  override fun rejected(rejected: (Throwable) -> Unit) = this

  override fun <SUB_RESULT> then(done: (T) -> SUB_RESULT): Promise<SUB_RESULT> {
    return if (done is Obsolescent && (done).isObsolete()) {
      RejectedPromise<SUB_RESULT>("obsolete")
    }
    else {
      ResolvedPromise(done(result))
    }
  }

  override fun notify(child: AsyncPromise<T>) {
    child.setResult(result)
  }
}