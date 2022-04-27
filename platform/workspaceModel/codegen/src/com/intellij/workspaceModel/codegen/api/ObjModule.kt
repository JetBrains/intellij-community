package org.jetbrains.deft.impl

import org.jetbrains.deft.impl.fields.ExtField

abstract class ObjModule {
  @RequiresOptIn
  annotation class InitApi


  internal lateinit var byId: Array<ObjType<*, *>?>
  private val _dependencies = mutableListOf<ObjModule>()
  @InitApi
  protected abstract fun init()

  protected fun beginInit(maxId: Int) {
    byId = arrayOfNulls(maxId)
  }

  @InitApi
  protected fun add(type: ObjType<*, *>) {
    byId[typeIndex(type.id)] = type
  }

  private var extFields: Array<ExtField<*, *>?>? = null

  @Volatile
  private var initialized = false

  @InitApi
  protected fun requireDependency(module: ObjModule) {
    if (!module.initialized) module.init()
    _dependencies.add(module)
  }

  @OptIn(InitApi::class)
  fun require(): ObjModule {
    if (!initialized) {
      synchronized(this) {
        if (!initialized) {
          init()
          link()
        }
      }
    }

    return this
  }

  @InitApi
  private fun link() {
    check(!initialized)

    _dependencies.forEach {
      if (!it.initialized) it.link()
    }

    byId.forEach {
      it?.link(this)
    }

    extFields?.forEach {
      it?.type?.link(this)
    }

    initialized = true
  }

  internal fun type(typeId: Int): ObjType<*, *> =
    byId[typeIndex(typeId)]!!

  internal fun typeIndex(id: Int) = id - 1
}

