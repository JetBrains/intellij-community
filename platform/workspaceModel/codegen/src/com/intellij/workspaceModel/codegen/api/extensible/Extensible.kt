package org.jetbrains.deft.obj.api.extensible

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.ExtensibleProvider
import org.jetbrains.deft.impl.fields.ExtField

interface Extensible : Obj, ExtensibleProvider {
  fun <R> unsafeGetExtension(): R?
  fun unsafeAddExtension(value: Any?)
}