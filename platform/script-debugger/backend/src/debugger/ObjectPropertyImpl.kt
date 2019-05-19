// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    const val WRITABLE: Int = 0x01
    const val CONFIGURABLE: Int = 0x02
    const val ENUMERABLE: Int = 0x04
  }

  override val isWritable: Boolean
    get() = BitUtil.isSet(flags, WRITABLE)

  override val isConfigurable: Boolean
    get() = BitUtil.isSet(flags, CONFIGURABLE)

  override val isEnumerable: Boolean
    get() = BitUtil.isSet(flags, ENUMERABLE)
}