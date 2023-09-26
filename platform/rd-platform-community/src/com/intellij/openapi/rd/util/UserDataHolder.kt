package com.intellij.openapi.rd.util

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.jetbrains.rd.util.assert
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

inline fun <T> UserDataHolder.getOrCreateUserData(key: Key<T>, producer: () -> T): T {
  val existing = getUserData(key)
  if (existing != null) return existing

  val value = producer()
  putUserData(key, value)
  return value
}

fun <T> UserDataHolder.putUserData(lt: Lifetime, key: Key<T>, value: T) {
  assert(getUserData(key) == null) { "getUserData($key) == null" }
  putUserData(key, value)
  lt.onTermination { putUserData(key, null) }
}

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
        return thisRef.getOrCreateUserData(getKey(property)) { lazyDefaultValue(thisRef) }
      }
    }

    override fun setValue(thisRef: TThis, property: KProperty<*>, value: TValue) {
      thisRef.putUserData(getKey(property), value)
    }
  }
}

fun <TThis : UserDataHolder, TValue> userData(key: Key<TValue>, lazyDefaultValue: (TThis) -> TValue): ReadWriteProperty<TThis, TValue> {
  return object : ReadWriteProperty<TThis, TValue> {
    override fun getValue(thisRef: TThis, property: KProperty<*>): TValue {
      return thisRef.getUserData(key) ?: synchronized(this) {
        return thisRef.getOrCreateUserData(key) { lazyDefaultValue(thisRef) }
      }
    }

    override fun setValue(thisRef: TThis, property: KProperty<*>, value: TValue) {
      thisRef.putUserData(key, value)
    }
  }
}