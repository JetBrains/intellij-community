package org.jetbrains.deft.obj.api.extensible

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.ExtensibleProvider
import org.jetbrains.deft.impl.fields.ExtField

interface Extensible : Obj, ExtensibleProvider {
    fun <R> unsafeGetExtension(field: ExtField<*, R>): R?
    fun unsafeRemoveExtension(field: ExtField<*, *>)
    fun unsafeAddExtension(field: ExtField<*, *>, value: Any?, raw: Boolean = false)
}