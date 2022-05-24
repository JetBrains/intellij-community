package org.jetbrains.deft.impl

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.deft.codegen.ijws.getRefType
import org.jetbrains.deft.codegen.ijws.refsFields
import org.jetbrains.deft.codegen.model.KtObjModule
import org.jetbrains.deft.impl.fields.ExtField

private val LOG = logger<ObjModule>()

abstract class ObjModule {
  @RequiresOptIn
  annotation class InitApi

  internal lateinit var byId: Array<ObjType<*, *>?>

  protected fun beginInit(maxId: Int) {
    byId = arrayOfNulls(maxId)
  }

  @InitApi
  protected fun add(type: ObjType<*, *>) {
    byId[typeIndex(type.id)] = type
  }

  val extFields = mutableListOf<ExtField<*, *>>()

  @Volatile
  private var initialized = false

  @OptIn(InitApi::class)
  fun require(): ObjModule {
    if (!initialized) {
      link()
      cleanUpUnrelatedReferences()
    }

    return this
  }

  @InitApi
  private fun link() {
    check(!initialized)

    byId.forEach { it?.link(this) }

    extFields.forEach {
      it.type.link(this)
    }

    initialized = true
  }

  private fun cleanUpUnrelatedReferences() {
    LOG.info("Ext fields count before cleanup ${extFields.size}")
    extFields.removeIf { extField ->
      val ref = extField.type.getRefType()
      if (ref.child) return@removeIf false
      val declaredReferenceFromChild =
        ref.targetObjType.structure.refsFields.filter { it.type.getRefType().targetObjType == extField.owner } +
        ((ref.targetObjType.module as? KtObjModule)?.extFields?.filter { it.type.getRefType().targetObjType == extField.owner && it.owner == ref.targetObjType && it != extField }
         ?: emptyList())
      if (declaredReferenceFromChild.isEmpty()) {
        LOG.info("Unrelated reference declared in one entity without @Child annotation will be skipped. It exist at ${extField.owner.name}#${extField.name} but absent at ${ref.targetObjType.name}")
        return@removeIf true
      }
      return@removeIf false
    }
    LOG.info("Ext fields count after cleanup ${extFields.size}")
  }

  internal fun type(typeId: Int): ObjType<*, *> =
    byId[typeIndex(typeId)]!!

  internal fun typeIndex(id: Int) = id - 1
}

