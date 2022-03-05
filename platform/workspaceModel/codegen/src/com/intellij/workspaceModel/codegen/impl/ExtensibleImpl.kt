package org.jetbrains.deft.impl

import com.intellij.workspaceModel.codegen.impl.ObjGraph
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.io.core.Input
import kotlinx.io.core.Output
import org.jetbrains.deft.Obj
import org.jetbrains.deft.bytes.intBytesCount
import org.jetbrains.deft.bytes.outputMaxBytes
import org.jetbrains.deft.impl.fields.ExtField
import org.jetbrains.deft.impl.fields.ExtFieldId
import org.jetbrains.deft.obj.api.extensible.Extensible

abstract class ExtensibleImpl : Extensible {
    private var extensionsSchema: PersistentMap<ExtField<*, *>, Int> = persistentHashMapOf()
    // todo: bytes storage
    private var extensions = arrayOfNulls<Any?>(0)

    open fun _markChanged() = Unit

    override fun getExtensibleContainer(): ExtensibleImpl = this

    private inline fun _forEachExtensionRaw(item: (field: ExtField<*, *>, value: Any?) -> Unit) {
        val fields = arrayOfNulls<ExtField<*, *>>(extensionsSchema.size)
        extensionsSchema.forEach { (field, slot) ->
            fields[slot] = field
        }
        fields.forEachIndexed { index, field ->
            val value = extensions[index]
            item(field!!, value)
        }
    }

    override fun forEachExtension(item: (field: ExtField<*, *>, value: Any?) -> Unit) {
        _forEachExtensionRaw { f, v -> item(f, f.type.extGetValue(this, v)) }
    }

    override fun forEachExtensionLazy(item: (field: ExtField<*, *>, value: () -> Any?) -> Unit) {
        _forEachExtensionRaw { f, v -> item(f) { f.type.extGetValue(this, v) } }
    }

    fun extensionsMoveIntoGraph(graph: ObjGraph?) {
        _forEachExtensionRaw { field, value ->
            field.type.moveIntoGraph(graph, value)
        }
    }

    fun extensionsUpdateRefIds() {
        _forEachExtensionRaw { field, value ->
            field.type.updateRefIds(value)
        }
    }
    
    fun extensionsEstimateMaxSize(): Int {
        var result = intBytesCount
        extensionsSchema.forEach { field, index ->
            field as ExtField<Obj, Any>
            result += field.name.outputMaxBytes + field.type.estimateMaxSize(extensions[index]!!)
        }
        return result
    }

    fun extensionsStoreTo(output: Output) {
        output.writeInt(extensions.size)
        _forEachExtensionRaw { field, value ->
            field.id.writeTo(output)
            (field as ExtField<Obj, Any?>).type.store(output, value)
        }
    }

    fun extensionsLoadFrom(data: Input, module: ObjModule) {
        repeat(data.readInt()) {
            val fieldId = ExtFieldId.read(data)
            // todo: ability to skip fields
            val fieldModule = module.modules[fieldId.moduleId] ?: error("unknown ext field: $fieldId (unknown module)")
            val field = fieldModule.getExtField(fieldId.localId) ?: error("unknown ext field: $fieldId (unknown local id)")
            val value = field.type.load(data, this as? ObjImpl)
            unsafeAddExtension(field, value, raw = true)
        }
    }

    override fun <R> unsafeGetExtension(field: ExtField<*, R>): R? {
        val i = extensionsSchema[field] ?: return null
        return maybeUnwrap(field, i)
    }

    private fun <R> maybeUnwrap(field: ExtField<*, R>, i: Int): R {
        field as ExtField<Obj, R>
        val src = extensions[i]!!
        return field.type.extGetValue(this, src) as R
    }

    override fun unsafeRemoveExtension(field: ExtField<*, *>) {
        _markChanged()
        val i = extensionsSchema[field]
        if (i != null) extensions[i] = null
    }

    override fun unsafeAddExtension(field: ExtField<*, *>, value: Any?, raw: Boolean) {
        _markChanged()
        field as ExtField<*, Any?>
        val actualValue = if (raw) value else field.type.extSetValue(this, value)
        val i = extensionsSchema[field]
        if (i != null) {
            extensions[i] = actualValue
        } else {
            val i1 = extensionsSchema.size
            extensions = extensions.copyOf(i1 + 1)
            extensions[i1] = actualValue
            extensionsSchema = extensionsSchema.put(field, i1)
        }
    }

    override fun <R> unsafeGetOrCreateExtension(field: ExtField<*, R>): R {
        val i = extensionsSchema[field]
        if (i != null) {
            return maybeUnwrap(field, i)
        } else {
            val i1 = extensionsSchema.size
            extensions = extensions.copyOf(i1 + 1)

            @Suppress("UNCHECKED_CAST")
            val ext_ = (field as ExtField<Obj, R>).newValue(this)
            val ext = field.type.extSetValue(this, ext_) as R
            extensions[i1] = ext
            extensionsSchema = extensionsSchema.put(field, i1)
            _markChanged()
            return ext
        }
    }

    override fun unsafeAddExtensions(fields: Array<ExtField<*, *>>, values: Array<Any>) {
        _markChanged()
        val oldSchema = extensionsSchema
        var s = oldSchema.size
        extensionsSchema = extensionsSchema.mutate { schema ->
            val newIds = IntArray(fields.size)
            var n = 0
            fields.forEachIndexed { index, field ->
                field as ExtField<*, Any?>
                val value = field.type.extSetValue(this, values[index])!!
                val i = oldSchema[field]
                if (i != null) {
                    extensions[i] = value
                } else {
                    schema[field] = s++
                    newIds[n++] = index
                }
            }

            if (n > 0) {
                var j = extensions.size
                extensions = extensions.copyOf(j + n)
                var i = 0
                while (i < n) extensions[j++] = values[newIds[i++]]
            }
        }
    }

    override fun unsafeGetOrCreateExtensions(vararg types: ExtField<*, *>): Array<*> {
        val oldSchema = extensionsSchema
        var s = oldSchema.size
        val result = arrayOfNulls<Any>(types.size)
        extensionsSchema = extensionsSchema.mutate { schema ->
            val newIds = IntArray(types.size)
            var n = 0
            types.forEachIndexed { index, field ->
                val i = oldSchema[field]
                if (i != null) {
                    result[index] = maybeUnwrap(field, i)
                } else {
                    schema[field] = s++
                    newIds[n++] = index

                    field as ExtField<*, Any?>
                    val newValue_ = (field as ExtField<Obj, *>).newValue(this)
                    val newValue = field.type.extSetValue(this, newValue_)
                    @Suppress("UNCHECKED_CAST")
                    result[index] = newValue
                    _markChanged()
                }
            }

            if (n > 0) {
                var j = extensions.size
                extensions = extensions.copyOf(j + n)
                var i = 0
                while (i < n) extensions[j++] = result[newIds[i++]]
            }
        }
        return result
    }

    companion object {
        val notWrapped = Any()
    }
}