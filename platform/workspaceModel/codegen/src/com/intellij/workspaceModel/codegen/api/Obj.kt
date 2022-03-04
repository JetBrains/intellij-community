package org.jetbrains.deft

import org.jetbrains.deft.impl.ExtensibleProvider
import org.jetbrains.deft.impl.ObjImpl
import org.jetbrains.deft.impl.ObjModule
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.obj.api.ExtensionsProvider
import org.jetbrains.deft.obj.impl.ObjImplWrapper
import org.jetbrains.deft.runtime.Runtime

interface Struct

// used as type parameter bounds in RelationsBuilder to workaround conflicts after generics erasing.
interface _Obj0 : Struct
interface _Obj1 : _Obj0

interface Obj : _Obj1 {
    val factory: ObjType<*, *>
    val name: String?
    val parent: Obj?

    fun <V> getValue(field: Field<*, V>): V = TODO()

    companion object {
        inline fun <reified T : Obj, V> extensions(): ExtensionsProvider<T, V> =
            @Suppress("UNCHECKED_CAST")
            (ExtensionsProvider(_objModule<T>(), T::class.java) { null as V })

        inline fun <reified T : Obj, V> extensions(default: V): ExtensionsProvider<T, V> =
            ExtensionsProvider(_objModule<T>(), T::class.java) { default }

        inline fun <reified T : Obj, V> extensions(noinline default: T.() -> V): ExtensionsProvider<T, V> =
            ExtensionsProvider(_objModule<T>(), T::class.java, default)
    }
}

inline fun <reified C> Obj.require(f: Obj.() -> Unit) {
    (this as ObjImplWrapper).impl.require(C::class.java) { f() }
}

interface TypeToken<T>

inline fun <reified ToForceInline> _objModule(): ObjModule {
    val c = (object : TypeToken<ToForceInline> {}).javaClass
    return ObjModule.modules.requireByPackageName(c.packageName, c.classLoader)
}

operator fun <T : Obj, V> T.get(field: Field<in T, V>): V = getValue(field)

interface Root : Obj {
    interface Builder : Root, ObjBuilder<Root>, ExtensibleProvider
    companion object : ObjType<Root, Builder>(Runtime, 1)
}

/**
 * Cast immutable object to mutable.
 * Will fail with exception if not.
 */
inline fun <reified B : ObjBuilder<*>> Obj.builder(): B {
    val impl = (this as ObjImplWrapper).impl
    impl.unfreeze()
    val builder = if (this is ObjBuilder<*>) this else impl.builder()
    return builder as? B
        ?: error(
            "$this is not ${B::class.java.simpleName}." +
                    " ObjBuilder cannot be down-casted as it contains mutable properties"
        )
}

/**
 * Updates in-place in case of [this] is mutable and owned by this thread.
 * Will fail with exception if not.
 */
inline fun <reified B : ObjBuilder<*>> Obj.update(f: B.() -> Unit): B {
    // todo: do what KDoc states
    val b = builder<B>()
    b.f()
    return b
}

/**
 * Updates in-place in case of [this] is mutable and owned by this thread.
 * Copy if not.
 */
inline fun <reified B : ObjBuilder<*>> Obj.updateOrCopy(f: B.() -> Unit): B {
    // todo: do what KDoc states
    val b = builder<B>()
    b.f()
    return b
}


val <T : Obj> T.id: ObjId<T>
    get() {
        val id = (this as ObjImpl)._id
        check(!id.isNothing())
        require(!id.isNewIdHolder()) { "Cannot get id of uncommitted object" }
        return id as ObjId<T>
    }

interface ObjBuilder<T : Obj> {
    val factory: ObjType<T, *>

    // todo: rename to hasValue
    fun hasNewValue(field: Field<in T, *>): Boolean = false

    // not `in T` to not clash with method in Obj
    fun <V> setValue(field: Field<in T, V>, value: V)

    fun build(): T
}

operator fun <T : Obj, V> ObjBuilder<T>.get(field: Field<in T, V>): V = (this as T).getValue(field)
operator fun <T : Obj, V> ObjBuilder<T>.set(field: Field<in T, V>, value: V): Unit = setValue(field, value)


fun alienFieldError(expectedObjType: ObjType<*, *>, field: Field<*, *>): Nothing {
    error("`$expectedObjType` has no `$field`")
}

fun extensionImpl(): Nothing? = TODO()
