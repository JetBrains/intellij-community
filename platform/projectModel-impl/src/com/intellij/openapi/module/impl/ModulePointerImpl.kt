/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.module.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModulePointer

import java.util.concurrent.atomic.AtomicReference

class ModulePointerImpl : ModulePointer {

  private val myModule: AtomicReference<Module>
  private var myModuleName: String? = null

  internal constructor(module: Module) {
    myModule = AtomicReference(module)
    myModuleName = null
  }

  internal constructor(name: String) {
    myModule = AtomicReference<Module>(null)
    myModuleName = name
  }

  override fun getModule(): Module? {
    return myModule.get()
  }

  override fun getModuleName(): String {
    val module = myModule.get()
    return module?.name ?: myModuleName
  }

  internal fun moduleAdded(module: Module): Boolean {
    if (!myModule.compareAndSet(null, module)) {
      return false
    }

    LOG.assertTrue(myModuleName == module.name)
    myModuleName = null
    return true
  }

  internal fun moduleRemoved(module: Module) {
    val resolvedModule = myModule.get()
    LOG.assertTrue(resolvedModule === module)
    myModuleName = resolvedModule.name
    myModule.set(null)
  }

  companion object {
    private val LOG = Logger.getInstance(ModulePointerImpl::class.java)
  }
}
