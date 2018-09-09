@file:Suppress("ClassName")

package com.intellij.configurationScript

private object UNINITIALIZED_VALUE

@Suppress("LocalVariableName")
internal class SynchronizedClearableLazy<out T>(private val initializer: () -> T) : Lazy<T> {
  @Volatile
  private var _value: Any? = UNINITIALIZED_VALUE

  override val value: T
    get() {
      val _v1 = _value
      if (_v1 !== UNINITIALIZED_VALUE) {
        @Suppress("UNCHECKED_CAST")
        return _v1 as T
      }

      return synchronized(this) {
        val _v2 = _value
        if (_v2 !== UNINITIALIZED_VALUE) {
          @Suppress("UNCHECKED_CAST") (_v2 as T)
        }
        else {
          val typedValue = initializer()
          _value = typedValue
          typedValue
        }
      }
    }

  override fun isInitialized(): Boolean = _value !== UNINITIALIZED_VALUE

  override fun toString(): String = if (isInitialized()) value.toString() else "Lazy value not initialized yet."

  fun drop() {
    synchronized(this) {
      _value = UNINITIALIZED_VALUE
    }
  }
}