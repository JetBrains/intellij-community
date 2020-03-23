// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api

class IntIntBiMultiMap {

  private var myKey2Values: IntIntMultiMap = IntIntMultiMap()
  private var myValue2Keys: IntIntMultiMap = IntIntMultiMap()

  fun getValues(key: Int, action: (Int) -> Unit) {
    myKey2Values.get(key, action)
  }

  fun getKeys(value: Int, action: (Int) -> Unit) {
    myValue2Keys.get(value, action)
  }

  fun containsKey(key: Int): Boolean {
    return key in myKey2Values
  }

  fun containsValue(value: Int): Boolean {
    return value in myValue2Keys
  }

  fun put(key: Int, value: Int) {
    myValue2Keys.put(value, key)
    myKey2Values.put(key, value)
  }

  /*
    fun removeKey(key: Int): Boolean {
      val vs: MutableSet<Any> = myKey2Values!![key] ?: return false
      for (v in vs) {
        val ks: MutableSet<K> = myValue2Keys!![v]!!
        ks.remove(key)
        if (ks.isEmpty()) {
          myValue2Keys!!.remove(v)
        }
      }
      myKey2Values!!.remove(key)
      return true
    }
  */

  fun remove(key: Int, value: Int) {
    myKey2Values.remove(key, value)
    myValue2Keys.remove(value, key)
  }

  fun isEmpty(): Boolean = myKey2Values.isEmpty() && myValue2Keys.isEmpty()

  /*
    fun removeValue(value: V): Boolean {
      val ks: MutableSet<Any> = myValue2Keys!![value] ?: return false
      for (k in ks) {
        val vs: MutableSet<V> = myKey2Values!![k]!!
        vs.remove(value)
        if (vs.isEmpty()) {
          myKey2Values!!.remove(k)
        }
      }
      myValue2Keys!!.remove(value)
      return true
    }
  */

  fun clear() {
    myKey2Values.clear()
    myValue2Keys.clear()
  }

/*
  fun getKeys(): Set<K>? {
    return myKey2Values!!.keys
  }

  fun getValues(): Set<V>? {
    return myValue2Keys!!.keys
  }
*/
}