package org.jetbrains.deft.json

import com.google.gson.stream.JsonWriter
import org.jetbrains.deft.Obj
import org.jetbrains.deft.get
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.obj.impl.ObjImplWrapper
import java.io.StringWriter

fun Obj.toHumanJson(shallow: Boolean = false): String =
    json { toHumanJson(it, shallow) }

fun Obj.toHumanJson(j: JsonWriter, shallow: Boolean = false) {
    value(j, factory.structure, this, shallow)
}

private fun value(
    j: JsonWriter,
    t: ValueType<*>,
    v: Any?,
    shallow: Boolean
) {
    when (t) {
        is TString -> j.value(v as String)
        is TBoolean -> j.value(v as Boolean)
        is TInt -> j.value(v as Int)
        is TOptional ->
            if (v == null) j.nullValue() else value(j, t.type, v, shallow)
        is TRef<*> -> {
            if (!shallow && t.child) {
                (v as Obj).toHumanJson(j)
            } else {
                j.value("obj:" + ((v as? Obj)?.name?.takeIf { it.isNotBlank() } ?: v.hashCode()
                    .toString())) // todo: path
            }
        }
        is TList<*> -> {
            j.beginArray()
            (v as List<*>).forEach {
                value(j, t.elementType, it, shallow)
            }
            j.endArray()
        }
        is TMap<*, *> -> {
            j.beginObject()
            (v as Map<*, *>).forEach {
                key(j, t.keyType, it.key, shallow)
                value(j, t.valueType, it.value, shallow)
            }
            j.endObject()
        }
        is TStructure<*, *> -> {
            j.beginObject()
            j.name("@type")
            j.value(t.box.packageName + "." + t.box.name)
            if (v is ObjImplWrapper) {
                j.name("@id").value(v.impl._id.n)
            }
            t.allFields.forEach {
                j.name(it.name)
                val vv = try {
                    (v as Obj)[it as Field<in Obj, Any?>]
                } catch (t: Throwable) {
                    t
                }
                if (vv is Throwable) j.value("ERROR: ${vv.stackTraceToString()}")
                else value(j, it.type, vv, shallow)
            }

            extensions(v, j, shallow)

            j.endObject()
        }
        is TBlob<*> -> TODO()
        else -> error(t)
    }
}

private fun extensions(v: Any?, j: JsonWriter, shallow: Boolean) {
    (v as? ExtensibleProvider)
        ?.getExtensibleContainer()
        ?.forEachExtension { field, value ->
            j.name(field.id.moduleId.notation + ":" + field.name)
            value(j, field.type, value, shallow)
        }
}

private fun key(
    j: JsonWriter,
    valueType: ValueType<*>,
    v: Any?,
    shallow: Boolean
) {
    if (valueType is TString) j.name(v as String)
    else {
        val keyInnerJson = json("") { value(it, valueType, v, shallow) }.removeSurrounding("\"")
        j.name(keyInnerJson)
    }
}

inline fun json(indent: String = "  ", w: (JsonWriter) -> Unit): String {
    val str = StringWriter()
    val jsonWriter = JsonWriter(str)
    jsonWriter.setIndent(indent)
    w(jsonWriter)
    return str.buffer.toString()
}