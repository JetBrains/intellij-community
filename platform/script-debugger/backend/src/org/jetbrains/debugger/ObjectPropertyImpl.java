package org.jetbrains.debugger

import com.intellij.util.BitUtil
import org.jetbrains.debugger.values.FunctionValue
import org.jetbrains.debugger.values.Value

class ObjectPropertyImpl(name: String,
                         value: Value?,
                         private val getter: FunctionValue?,
                         private val setter: FunctionValue?,
                         valueModifier: ValueModifier?,
                         private val flags: Int) : VariableImpl(name, value, valueModifier), ObjectProperty {

  override fun getGetter(): FunctionValue? {
    return getter
  }

  override fun getSetter(): FunctionValue? {
    return setter
  }

  override fun isWritable(): Boolean {
    return BitUtil.isSet(flags, WRITABLE.toInt())
  }

  override fun isConfigurable(): Boolean {
    return BitUtil.isSet(flags, CONFIGURABLE.toInt())
  }

  override fun isEnumerable(): Boolean {
    return BitUtil.isSet(flags, ENUMERABLE.toInt())
  }

  companion object {
    val WRITABLE: Byte = 0x01
    val CONFIGURABLE: Byte = 0x02
    val ENUMERABLE: Byte = 0x04
  }
}