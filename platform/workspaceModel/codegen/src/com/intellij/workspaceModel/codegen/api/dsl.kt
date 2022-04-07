// this package should be always start imported
package org.jetbrains.deft

import org.jetbrains.deft.impl.fields.ExtField
import org.jetbrains.deft.impl.ExtensibleProvider

fun <T : Obj, R> T.getExtension(extType: ExtField<*, R>): R? =
    (this as ExtensibleProvider).getExtensibleContainer().unsafeGetExtension(extType)

fun <T : Obj, B : ObjBuilder<T>, R> B.addExtension(extType: ExtField<T, R>, value: R) {
    (this as ExtensibleProvider).getExtensibleContainer().unsafeAddExtension(extType, value)
}

fun <T : Obj, B : ObjBuilder<T>, R> B.removeExtension(extType: ExtField<T, R>) {
    (this as ExtensibleProvider).getExtensibleContainer().unsafeRemoveExtension(extType)
}

//inline operator fun <T : Obj, B : ObjBuilder<T>> T.invoke(f: B.() -> Unit): T {
//    f()
//}

interface ObjectsListBuilder<T : _Obj0, B : T> : MutableList<B> {
    fun add(item: T): Boolean = add(item as B)
}

@Suppress("CONFLICTING_INHERITED_JVM_DECLARATIONS")
interface RelationsBuilder<RT : _Obj0, RB : RT, T : _Obj1, B : T> : ObjectsListBuilder<RT, RB> {
    fun createRelation(target: T): RB

    fun add(target: T): Boolean = super.add(createRelation(target))
}

// experimental `val x by Type { ... }` DSL
//operator fun <B : ObjBuilder> B.getValue(unused: Any?, property: KProperty<*>): B {
//    name = property.name
//    return this
//}
