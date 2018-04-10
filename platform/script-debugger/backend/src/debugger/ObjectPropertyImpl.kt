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
package org.jetbrains.debugger

import com.intellij.util.BitUtil
import org.jetbrains.debugger.values.FunctionValue
import org.jetbrains.debugger.values.Value

class ObjectPropertyImpl(name: String,
                         value: Value?,
                         override val getter: FunctionValue? = null,
                         override val setter: FunctionValue? = null,
                         valueModifier: ValueModifier? = null,
                         private val flags: Int = 0) : VariableImpl(name, value, valueModifier), ObjectProperty {
  companion object {
    val WRITABLE = 0x01
    val CONFIGURABLE = 0x02
    val ENUMERABLE = 0x04
  }

  override val isWritable: Boolean
    get() = BitUtil.isSet(flags, WRITABLE)

  override val isConfigurable: Boolean
    get() = BitUtil.isSet(flags, CONFIGURABLE)

  override val isEnumerable: Boolean
    get() = BitUtil.isSet(flags, ENUMERABLE)
}