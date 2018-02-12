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

open class PrimitiveValue(type: ValueType, override val valueString: String) : ValueBase(type) {

  constructor(type: ValueType, value: Int) : this(type, Integer.toString(value)) {
  }

  constructor(type: ValueType, value: Long) : this(type, java.lang.Long.toString(value)) {
  }

  companion object {
    val NA_N_VALUE = "NaN"
    val INFINITY_VALUE = "Infinity"

    @JvmField
    val NULL = PrimitiveValue(ValueType.NULL, "null")
    @JvmField
    val UNDEFINED = PrimitiveValue(ValueType.UNDEFINED, "undefined")

    val NAN = PrimitiveValue(ValueType.NUMBER, NA_N_VALUE)
    val INFINITY = PrimitiveValue(ValueType.NUMBER, INFINITY_VALUE)

    private val TRUE = PrimitiveValue(ValueType.BOOLEAN, "true")
    private val FALSE = PrimitiveValue(ValueType.BOOLEAN, "false")

    fun bool(value: String): PrimitiveValue {
      return if (value == "true") TRUE else FALSE
    }
  }
}