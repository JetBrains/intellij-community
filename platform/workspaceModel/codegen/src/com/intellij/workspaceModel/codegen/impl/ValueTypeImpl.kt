package org.jetbrains.deft.impl

import com.intellij.workspaceModel.codegen.impl.ObjGraph
import org.jetbrains.deft.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.obj.impl.ObjImplWrapper

@Suppress("UNCHECKED_CAST")
fun <V> ValueType<V>.wrapperView(obj: ObjImpl?): ValueView<V, Any?> =
    when (this) {
        is TList<*> -> listView(obj)
        is TMap<*, *> -> mapView(obj)
        is TOptional<*> -> optionalView(obj)
        is TRef<*> -> refView(obj)
        else -> ValueView.id
    } as ValueView<V, Any?>


fun <V> ValueType<V>.moveIntoGraph(graph: ObjGraph?, v: Any?) {
    when (this) {
        is TOptional<*> -> if (v != null) type.moveIntoGraph(graph, v)
        is TRef<*> -> (v as Ref<*>?)?.ensureInGraph(graph)
        is TList<*> -> (v as ListView<Any?, Any?>?)?.src?.forEach {
            elementType.moveIntoGraph(graph, it)
        }
        is TMap<*, *> -> (v as MapView<Any?, Any?, Any?, Any?>?)?.src?.forEach {
            keyType.moveIntoGraph(graph, it.key)
            valueType.moveIntoGraph(graph, it.value)
        }
        is TStructure<*, *> -> TODO()
        is TBlob<*> -> TODO()
        else -> Unit
    }
}

fun <V> ValueType<V>.checkInitialized(obj: ObjImpl, field: Field<*, V>, v: V?) {
    when (this) {
        TBoolean,
        TInt,
        TString,
        is TRef<*>,
        is TBlob<*> -> if (v == null) throw MissedValue(obj, field, null) else Unit
        else -> Unit
    }
}

@Suppress("UNCHECKED_CAST")
fun <V> ValueType<V>.extGetValue(obj: ExtensibleImpl, value: Any?): V =
    when (this) {
        is TOptional<*> -> if (value == null) null else type.extGetValue(obj, value)
        is TRef<*> -> if (value is Ref<*>) value.get((obj as? ObjImplWrapper)?.impl?.graph) else value
        is TList<*> -> value ?: listOf<Any>() // todo: create and put mutable
        is TMap<*, *> -> value ?: mapOf<Any, Any>() // todo: create and put mutable
        is TStructure<*, *> -> TODO()
        is TBlob<*> -> value
        else -> value
    } as V

@Suppress("UNCHECKED_CAST")
fun <V> ValueType<V>.extSetValue(obj: ExtensibleImpl, value: V): Any? {
    if (obj !is ObjImplWrapper) return value
    val impl = obj.impl
    return when (this) {
        is TOptional<*> -> if (value == null) null else (type as ValueType<Any>).extSetValue(obj, value)
        is TRef<*> -> {
            val valueImpl = (value as ObjImplWrapper).impl
            impl._addRef(valueImpl)
            Ref(valueImpl._id as ObjId<Obj>, valueImpl)
        }
        is TList<*> -> {
            val l = newList(impl)
            l.addAll(value as List<*>)
            l
        }
        is TMap<*, *> -> {
            val r = newMap(impl)
            (r as MapView<Any?, Any?, Any?, Any?>).putAll(value as Map<*, *>)
            r
        }
        is TOptional<*> -> {
            if (value == null) null
            else (type as ValueType<Any?>).extSetValue(obj, value)
        }
        else -> value
    }
}
