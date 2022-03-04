package org.jetbrains.deft.obj.api.extensible

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.ExtensibleProvider
import org.jetbrains.deft.impl.fields.ExtField

interface Extensible : Obj, ExtensibleProvider {
    fun forEachExtension(item: (field: ExtField<*, *>, value: Any?) -> Unit)
    fun forEachExtensionLazy(item: (field: ExtField<*, *>, value: () -> Any?) -> Unit)

    fun <R> unsafeGetExtension(field: ExtField<*, R>): R?
    fun unsafeRemoveExtension(field: ExtField<*, *>)
    fun unsafeAddExtension(field: ExtField<*, *>, value: Any?, raw: Boolean = false)

    fun <R> unsafeGetOrCreateExtension(field: ExtField<*, R>): R
    fun unsafeAddExtensions(fields: Array<ExtField<*, *>>, values: Array<Any>)
    fun unsafeGetOrCreateExtensions(vararg types: ExtField<*, *>): Array<*>
}