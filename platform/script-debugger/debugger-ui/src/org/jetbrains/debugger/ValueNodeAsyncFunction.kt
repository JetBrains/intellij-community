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
package org.jetbrains.debugger

import com.intellij.util.Consumer
import org.jetbrains.concurrency.AsyncFunction
import org.jetbrains.concurrency.Obsolescent
import org.jetbrains.concurrency.Promise

abstract class ValueNodeAsyncFunction<PARAM, RESULT> protected constructor(private val node: Obsolescent) : AsyncFunction<PARAM, RESULT>, Obsolescent {
  override fun isObsolete() = node.isObsolete
}

inline fun <T, SUB_RESULT> Promise<T>.thenAsync(node: Obsolescent, crossinline handler: (T) -> Promise<SUB_RESULT>) = then(object : ValueNodeAsyncFunction<T, SUB_RESULT>(node) {
  override fun `fun`(param: T) = handler(param)
})

@Suppress("UNCHECKED_CAST")
inline fun <T> Promise<T>.thenAsyncVoid(node: Obsolescent, crossinline handler: (T) -> Promise<*>) = then(object : ValueNodeAsyncFunction<T, Any?>(node) {
  override fun `fun`(param: T) = handler(param) as Promise<Any?>
})

inline fun <T> Promise<T>.done(node: Obsolescent, crossinline handler: (T) -> Unit) = done(object : ObsolescentConsumer<T>(node) {
  override fun consume(param: T) = handler(param)
})

inline fun <T> Promise<T>.rejected(node: Obsolescent, crossinline handler: (Throwable) -> Unit) = rejected(object : ObsolescentConsumer<Throwable>(node) {
  override fun consume(param: Throwable) = handler(param)
})

abstract class ObsolescentConsumer<T>(private val obsolescent: Obsolescent) : Obsolescent, Consumer<T> {
  override fun isObsolete() = obsolescent.isObsolete
}