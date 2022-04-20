package org.jetbrains.deft.impl

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.fields.ExtField
import org.jetbrains.deft.obj.api.extensible.Extensible

abstract class ExtensibleImpl : Extensible {
  private var extensionsSchema: PersistentMap<ExtField<*, *>, Int> = persistentHashMapOf()

  // todo: bytes storage
  private var extensions = arrayOfNulls<Any?>(0)

  open fun _markChanged() = Unit

  override fun getExtensibleContainer(): ExtensibleImpl = this

  // +++
  override fun <R> unsafeGetExtension(field: ExtField<*, R>): R? {
    val i = extensionsSchema[field] ?: return null
    return maybeUnwrap(field, i)
  }

  // +++
  private fun <R> maybeUnwrap(field: ExtField<*, R>, i: Int): R {
    field as ExtField<Obj, R>
    val src = extensions[i]!!
    return field.type.extGetValue(this, src)
  }

  override fun unsafeRemoveExtension(field: ExtField<*, *>) {
    _markChanged()
    val i = extensionsSchema[field]
    if (i != null) extensions[i] = null
  }

  // +++
  override fun unsafeAddExtension(field: ExtField<*, *>, value: Any?, raw: Boolean) {
    _markChanged()
    field as ExtField<*, Any?>
    val actualValue = if (raw) value else field.type.extSetValue(this, value)
    val i = extensionsSchema[field]
    if (i != null) {
      extensions[i] = actualValue
    }
    else {
      val i1 = extensionsSchema.size
      extensions = extensions.copyOf(i1 + 1)
      extensions[i1] = actualValue
      extensionsSchema = extensionsSchema.put(field, i1)
    }
  }
}