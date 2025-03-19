package com.intellij.openapi.rd.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.jetbrains.rd.util.assert
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.ApiStatus
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

val <T> T.lifetime: Lifetime where T : UserDataHolder, T : Disposable by userData(Key("com.jetbrains.rd.platform.util.lifetime")) {
  it.createLifetime()
}

@ApiStatus.Internal
fun <T> UserDataHolder.putUserData(lt: Lifetime, key: Key<T>, value: T) {
  assert(getUserData(key) == null) { "getUserData($key) == null" }
  putUserData(key, value)
  lt.onTermination { putUserData(key, null) }
}

@ApiStatus.Internal
fun <T> userData(key: Key<T>): ReadWriteProperty<UserDataHolder, T?> {
  return object : ReadWriteProperty<UserDataHolder, T?> {
    override fun getValue(thisRef: UserDataHolder, property: KProperty<*>): T? {
      return thisRef.getUserData(key)
    }

    override fun setValue(thisRef: UserDataHolder, property: KProperty<*>, value: T?) {
      thisRef.putUserData(key, value)
    }
  }
}

@ApiStatus.Internal
fun <T> userData(name: String? = null): ReadWriteProperty<UserDataHolder, T?> {
  return object : ReadWriteProperty<UserDataHolder, T?> {
    private var key: Key<T>? = name?.let { Key.create(name) }
    private fun getKey(property: KProperty<*>): Key<T> {
      if (key == null) {
        key = Key.create(property.name + "by userData()")
      }
      return key as Key<T>
    }

    override fun getValue(thisRef: UserDataHolder, property: KProperty<*>): T? {
      return thisRef.getUserData(getKey(property))
    }

    override fun setValue(thisRef: UserDataHolder, property: KProperty<*>, value: T?) {
      thisRef.putUserData(getKey(property), value)
    }
  }
}

@ApiStatus.Internal
fun <TThis : UserDataHolder, TValue> userData(lazyDefaultValue: (TThis) -> TValue): ReadWriteProperty<TThis, TValue> {
  return object : ReadWriteProperty<TThis, TValue> {
    private var key: Key<TValue>? = null
    private fun getKey(property: KProperty<*>): Key<TValue> {
      if (key == null) {
        key = Key.create(property.name + "by userData()")
      }
      return key as Key<TValue>
    }

    override fun getValue(thisRef: TThis, property: KProperty<*>): TValue {
      return thisRef.getUserData(getKey(property)) ?: synchronized(this) {
        return thisRef.getOrCreateUserDataUnsafe(getKey(property)) { lazyDefaultValue(thisRef) }
      }
    }

    override fun setValue(thisRef: TThis, property: KProperty<*>, value: TValue) {
      thisRef.putUserData(getKey(property), value)
    }
  }
}

@ApiStatus.Internal
fun <TThis : UserDataHolder, TValue> userData(key: Key<TValue>, lazyDefaultValue: (TThis) -> TValue): ReadWriteProperty<TThis, TValue> {
  return object : ReadWriteProperty<TThis, TValue> {
    override fun getValue(thisRef: TThis, property: KProperty<*>): TValue {
      return thisRef.getUserData(key) ?: synchronized(this) {
        return thisRef.getOrCreateUserDataUnsafe(key) { lazyDefaultValue(thisRef) }
      }
    }

    override fun setValue(thisRef: TThis, property: KProperty<*>, value: TValue) {
      thisRef.putUserData(key, value)
    }
  }
}