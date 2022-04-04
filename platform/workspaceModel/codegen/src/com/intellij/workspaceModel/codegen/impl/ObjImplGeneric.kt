package org.jetbrains.deft.impl

import com.intellij.workspaceModel.codegen.impl.ObjGraph
import kotlinx.io.core.Input
import kotlinx.io.core.Output
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.obj.impl.ObjBuilderImplWrapper
import org.jetbrains.deft.obj.impl.ObjImplWrapper
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class ObjImplGeneric<T : Obj, B : ObjBuilder<T>>(
    override val factory: ObjType<T, B>
) : ObjImpl(), InvocationHandler {
    private var values = arrayOfNulls<Any?>(factory.structure.maxFieldId - factory.structure.minFieldId)

    private val <V> Field<out T, V>.index
        get() = id - this@ObjImplGeneric.factory.structure.minFieldId

    operator fun <V> get(field: Field<T, V>): V =
        field.type.extGetValue(this, values[field.index])

    @Suppress("UNCHECKED_CAST")
    override fun <V> getValue(field: Field<*, V>): V =
        get(field as Field<T, V>)

    operator fun <V> set(field: Field<T, V>, v: V) {
        values[field.index] = field.type.extSetValue(this, v)
    }

    private inline fun forEach(item: (field: Field<out T, Any?>, value: Any?) -> Unit) {
        factory.structure.allFields.forEach { item(it, values[it.index]) }
    }

    override fun checkInitialized() {
        forEach { field, value -> field.type.checkInitialized(this, field, value) }
    }

    override fun hasNewValue(field: Field<*, *>): Boolean {
        @Suppress("UNCHECKED_CAST")
        return values[(field as Field<out T, *>).index] != null
    }

    override fun updateRefIds() {
        super.updateRefIds()
        forEach { field, value -> field.type.updateRefIds(value) }
    }

    override fun moveIntoGraph(graph: ObjGraph?) {
        super.moveIntoGraph(graph)
        forEach { field, value -> field.type.moveIntoGraph(graph, value) }
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        return when (method.name) {
            "getImpl" -> this
            "getExtensibleContainer" -> this
            "getFactory" -> factory
            "getValue" -> getValue(args!![0] as Field<*, Any?>)
            "getName" -> name
            "getParent" -> parent
            else -> {
                val name = method.name

                when {
                    name.startsWith("get") -> {
                        val field = factory.findFieldByAccessorName(name)
                        if (field != null) return getValue(field)
                    }
                }

                error(method)
            }
        }
    }

    override fun builder(): ObjBuilder<*> {
        return Builder.wrap(this)
    }

    class Builder<T : Obj>(
        override val result: ObjImplGeneric<T, *>
    ) : Obj, ObjBuilderImpl<T>(), InvocationHandler {
        override val unsafeResultInstance: T =
            Proxy.newProxyInstance(
                result.factory.javaClass.classLoader,
                arrayOf(
                    ExtensibleProvider::class.java,
                    ObjImplWrapper::class.java,
                    result.factory.ival
                ),
                result
            ) as T

        override var name: String?
            get() = result.name
            set(value) {
                result._name = value
            }

        override var parent: Obj?
            get() = result.parent
            set(value) {
                result.setParent(value)
            }

        override fun <V> getValue(field: Field<*, V>): V {
            return result.getValue(field)
        }

        @Suppress("UNCHECKED_CAST")
        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            return when (method.name) {
                "getImpl" -> result
                "getUnsafeResultInstance" -> unsafeResultInstance
                "getExtensibleContainer" -> result
                "build" -> build()
                "getFactory" -> factory
                "getValue" -> result.getValue(args!![0] as Field<*, Any?>)
                "getName" -> name
                "getParent" -> parent
                "setName" -> name = args!![0] as String
                "setParent" -> parent = args!![0] as Obj
                else -> {
                    val name = method.name

                    when {
                        name.startsWith("get") -> {
                            val field = factory.findFieldByAccessorName(name)
                            if (field != null) return getValue(field)
                        }
                        name.startsWith("set") -> {
                            return factory.findFieldByAccessorName(name)
                        }
                    }

                    error(method)
                }
            }
        }

        companion object {
            @Suppress("UNCHECKED_CAST")
            fun <B : ObjBuilder<*>> wrap(impl: ObjImplGeneric<*, B>): B =
                Proxy.newProxyInstance(
                    impl.factory.javaClass.classLoader,
                    arrayOf(
                        ExtensibleProvider::class.java,
                        ObjBuilderImplWrapper::class.java,
                        impl.factory.ivar
                    ),
                    Builder(impl)
                ) as B

            operator fun <T : Obj, B : ObjBuilder<T>> invoke(type: ObjType<T, B>): B =
                wrap(ObjImplGeneric(type))

            @Suppress("UNCHECKED_CAST")
            fun <T : Obj> of(obj: ObjBuilder<T>): Builder<T> =
                Proxy.getInvocationHandler(obj) as Builder<T>
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T : Obj> of(obj: T): ObjImplGeneric<T, *> =
            Proxy.getInvocationHandler(obj) as ObjImplGeneric<T, *>

        @Suppress("UNCHECKED_CAST")
        private fun <T : Obj> ObjType<T, *>.findFieldByAccessorName(name: String): Field<in T, Any?>? {
            val fieldName = name.substring(3) // remove "get"/"set"
                .replaceFirstChar { it.lowercase() }
            val field = structure.allFieldsByName[fieldName]
            return field as Field<in T, Any?>?
        }
    }
}