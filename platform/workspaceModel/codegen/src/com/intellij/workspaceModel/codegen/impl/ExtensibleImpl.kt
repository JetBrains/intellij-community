package org.jetbrains.deft.impl

import org.jetbrains.deft.obj.api.extensible.Extensible

abstract class ExtensibleImpl : Extensible {
  private var extensionsSchema: Int? = null

  // todo: bytes storage
  private var extensions = arrayOfNulls<Any?>(0)
  override fun getExtensibleContainer(): ExtensibleImpl = this

  // +++
  override fun <R> unsafeGetExtension(): R? {
    val i = extensionsSchema ?: return null
    return extensions[i] as? R
  }

  // +++
  override fun unsafeAddExtension(value: Any?) {
    val i = extensionsSchema
    if (i != null) {
      extensions[i] = value
    }
    else {
      val i1 = 0
      extensions = extensions.copyOf(i1 + 1)
      extensions[i1] = value
      extensionsSchema = i1
    }
  }
}