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
package com.jetbrains.reactivemodel.util

import com.github.krukow.clj_lang.IPersistentMap
import com.github.krukow.clj_lang.PersistentHashMap


public fun withEmptyMeta<K, V>(map : PersistentHashMap<K, V>): PersistentHashMap<K, V> {
  return withMeta(map, emptyMeta());
}

@suppress("UNCHECKED_CAST")
public fun withMeta<K, V>(map : PersistentHashMap<K, V>, meta: IPersistentMap<String, *>): PersistentHashMap<K, V> {
  return map.withMeta(meta) as PersistentHashMap<K, V>;
}

@suppress("UNCHECKED_CAST")
public fun emptyMeta() : IPersistentMap<String, *>  = PersistentHashMap.EMPTY as IPersistentMap<String, *>

public fun createMeta(vararg objects: Any) : IPersistentMap<String, *> = PersistentHashMap.create<String, Any>(*objects)

fun IPersistentMap<String, *>.lifetime(): Lifetime = this.valAt("lifetime") as Lifetime
fun IPersistentMap<String, *>.get(key: String): Any? = this.valAt(key)