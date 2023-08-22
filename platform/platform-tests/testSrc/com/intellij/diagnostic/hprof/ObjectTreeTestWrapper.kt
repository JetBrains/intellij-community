/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof

import com.intellij.openapi.Disposable

class ObjectTreeTestWrapper {

  private val tree: Any = treeClazz.declaredConstructors.first { it.parameterCount == 0 }.apply { isAccessible = true }.newInstance()

  fun getTree(): Any {
    return tree
  }

  fun register(parent: Disposable, child: Disposable) {
    val method = treeClazz.getDeclaredMethod("register", Disposable::class.java, Disposable::class.java).apply { isAccessible = true }
    method.invoke(tree, parent, child)
  }

  companion object {
    private val treeClazz = Class.forName("com.intellij.openapi.util.ObjectTree")
  }
}