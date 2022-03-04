package org.jetbrains.deft.obj.api

import org.jetbrains.deft.Obj
import org.jetbrains.deft.impl.ObjModule
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.fields.ExtField
import kotlin.reflect.KProperty

class ExtensionsProvider<T : Obj, V>(
    val objModule: ObjModule,
    receiverClass: Class<T>,
    val default: T.() -> V
) {
    @Suppress("UNCHECKED_CAST")
    val receiver: ObjType<T, *> =
        receiverClass.declaredFields
            .find { it.name == "Companion" }!!
            .get(null) as ObjType<T, *>

    @Suppress("UNCHECKED_CAST")
    operator fun provideDelegate(
        nothing: Nothing?,
        prop: KProperty<*>,
    ): ExtField<T, V> {
        val result = objModule._extKotlinProps[ExtFieldKotlinId(receiver, prop.name)] as ExtField<T, V>
        result.default = default
        return result
    }
}