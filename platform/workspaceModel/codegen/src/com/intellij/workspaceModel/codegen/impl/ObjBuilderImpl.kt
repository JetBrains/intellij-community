package org.jetbrains.deft.impl

import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.obj.impl.ObjBuilderImplWrapper
import org.jetbrains.deft.obj.impl.ObjImplWrapper

abstract class ObjBuilderImpl<T : Obj> :
    ObjBuilder<T>,
    ObjImplWrapper,
    ExtensibleProvider,
    ObjBuilderImplWrapper<T> {
    // about differences between result and unsafeResultInstance:
    // when T implemented via java.Proxy, handler acts as ObjImpl, while T is separate instance
    // thus:
    //  - result (which should renamed to resultImpl) is ObjImpl instance,
    //  - unsafeResultInstance — T instance
    // for generated implementations both are same instance

    // todo: rename to resultImpl
    abstract val result: ObjImpl

    override val impl: ObjImpl
        get() = result

    override val name: String?
        get() = result.name

    override val parent: Obj?
        get() = result.parent

    @Suppress("UNCHECKED_CAST")
    override val unsafeResultInstance: T
        get() = result as T

    @Suppress("UNCHECKED_CAST")
    override val factory: ObjType<T, *>
        get() = result.factory as ObjType<T, *>

    override fun hasNewValue(field: Field<in T, *>): Boolean =
        result.hasNewValue(field as Field<*, *>)

    @Suppress("UNCHECKED_CAST")
    override fun build(): T {
        result.freeze()
        return unsafeResultInstance
    }

    override fun getExtensibleContainer(): ExtensibleImpl = result.getExtensibleContainer()
}