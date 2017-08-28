/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.debugger.values

import org.jetbrains.concurrency.Obsolescent
import java.util.concurrent.atomic.AtomicInteger

/**
 * The main idea of this class - don't create value for remote value handle if already exists. So,
 * implementation of this class keep map of value to remote value handle.
 * Also, this class maintains cache timestamp.

 * Currently WIP implementation doesn't keep such map due to protocol issue. But V8 does.
 */
abstract class ValueManager() : Obsolescent {
  private val cacheStamp = AtomicInteger()
  @Volatile private var obsolete = false

  open fun clearCaches() {
    cacheStamp.incrementAndGet()
  }

  fun getCacheStamp() = cacheStamp.get()

  override final fun isObsolete() = obsolete

  fun markObsolete() {
    obsolete = true
  }
}