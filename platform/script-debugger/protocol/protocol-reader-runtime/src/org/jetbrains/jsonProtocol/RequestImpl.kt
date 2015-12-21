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
package org.jetbrains.jsonProtocol

abstract class RequestImpl<T> : OutMessage(), Request<T> {
  private var argumentsObjectStarted = false

  protected abstract val idKeyName: String

  protected abstract fun argumentsKeyName(): String

  override fun beginArguments() {
    if (!argumentsObjectStarted) {
      argumentsObjectStarted = true
      writer.name(argumentsKeyName())
      writer.beginObject()
    }
  }

  override fun finalize(id: Int) {
    if (argumentsObjectStarted) {
      writer.endObject()
    }
    writer.name(idKeyName).value(id.toLong())
    writer.endObject()
    writer.close()
  }
}
