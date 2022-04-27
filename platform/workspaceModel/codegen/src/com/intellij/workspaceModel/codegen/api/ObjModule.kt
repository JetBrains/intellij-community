package org.jetbrains.deft.impl

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.ExtField
import org.jetbrains.deft.impl.fields.ExtFieldId
import org.jetbrains.deft.obj.api.ExtFieldKotlinId
import kotlin.reflect.KProperty

abstract class ObjModule(
  val id: Id,
  //val version: SemVer = SemVer(0, 0)
) {
  val modules: ObjModules
    get() = ObjModule.modules

  /**
   * Example: `org.jetbrains.deft.intellijWs`
   * will be parsed as:
   * - `org.jetbrains.deft.intellijWs` package
   * - `org.jetbrains.deft.intellijWs.IntellijWs` object name
   **/
  @JvmInline
  value class Id(val notation: String = "default") {
  }

  @RequiresOptIn
  annotation class InitApi

  companion object {
    val modules = ObjModules()
  }

  init {
    modules.byId[id] = this
  }

  internal lateinit var byId: Array<ObjType<*, *>?>
  private val _dependencies = mutableListOf<ObjModule>()
  val dependencies: List<ObjModule>
    get() = _dependencies.toList()
  val types: Collection<ObjType<*, *>>
    get() = byId.filterNotNull()
  val lastId: Int
    get() = byId.size

  @InitApi
  protected abstract fun init()

  protected fun beginInit(maxId: Int) {
    byId = arrayOfNulls(maxId)
  }

  @InitApi
  protected fun add(type: ObjType<*, *>) {
    byId[modules.typeIndex(type.id)] = type
  }

  private var extFields: Array<ExtField<*, *>?>? = null

  fun getExtField(localId: Int): ExtField<*, *>? =
    extFields?.getOrNull(localId - 1)

  fun beginExtFieldsInit(maxId: Int) {
    extFields = arrayOfNulls(maxId)
  }

  fun registerExtField(f: ExtField<*, *>) {
    check(f.id.moduleId == id)
    extFields!![f.id.localId - 1] = f
  }

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
      synchronized(modules) {
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
      it?.link(modules)
    }

    extFields?.forEach {
      it?.type?.link(modules)
    }

    initialized = true
  }

  internal fun type(typeId: Int): ObjType<*, *> =
    byId[modules.typeIndex(typeId)]!!

  val _extKotlinProps = mutableMapOf<ExtFieldKotlinId, ExtField<*, *>>()

  fun <T : Obj, V> defExt(
    id: Int,
    receiver: ObjType<T, *>,
    type: ValueType<V>,
  ): ExtFieldProvider<T, V> =
    ExtFieldProvider(extFieldId(id), receiver, type)

  private fun extFieldId(i: Int): ExtFieldId = ExtFieldId(id, i)

  inner class ExtFieldProvider<T : Obj, V>(
    val id: ExtFieldId,
    val receiver: ObjType<T, *>,
    val type: ValueType<V>
  ) {
    operator fun provideDelegate(
      nothing: Nothing?,
      prop: KProperty<*>,
    ): ExtField<T, V> {
      val extField = ExtField(id, receiver, prop.name, type)
      check(_extKotlinProps.put(ExtFieldKotlinId(receiver, prop.name), extField) == null) {
        "`${prop.name}` ext field redeclaration"
      }
      return extField
    }
  }
}

