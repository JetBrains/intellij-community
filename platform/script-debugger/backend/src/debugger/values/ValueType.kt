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

private val VALUE_TYPES = ValueType.values()

/**
 * Don't forget to update NashornDebuggerSupport.ValueType and DebuggerSupport.ts respectively also
 */
enum class ValueType {
  OBJECT,
  NUMBER,
  STRING,
  FUNCTION,
  BOOLEAN,

  ARRAY,
  NODE,

  UNDEFINED,
  NULL,
  SYMBOL;

  /**
   * Returns whether `type` corresponds to a JsObject. Note that while 'null' is an object
   * in JavaScript world, here for API consistency it has bogus type [.NULL] and is
   * not a [ObjectValue]
   */
  val isObjectType: Boolean
    get() = this == OBJECT || this == ARRAY || this == FUNCTION || this == NODE

  companion object {
    fun fromIndex(index: Int) = VALUE_TYPES.get(index)
  }
}