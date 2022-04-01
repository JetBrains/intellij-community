package org.jetbrains.deft.json

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.jetbrains.deft.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import java.io.StringReader

fun Obj.from(j: JsonReader) {
    value(j, factory.structure)
}

@OptIn(ExperimentalStdlibApi::class)
fun value(
    j: JsonReader,
    t: ValueType<*>
): Any? {
    return when (t) {
        is TString -> j.nextString()
        is TBoolean -> j.nextBoolean()
        is TInt -> j.nextInt()
        is TOptional -> when (j.peek()) {
            JsonToken.NULL -> null
            else -> value(j, t.type)
        }
        is TRef<*> -> when (val peek = j.peek()) {
            JsonToken.BEGIN_OBJECT -> TODO()
            JsonToken.STRING -> TODO()
            else -> error(peek)
        }
        is TList<*> -> {
            j.beginArray()
            buildList {
                while (j.peek() != JsonToken.END_ARRAY) {
                    add(value(j, t.elementType))
                }
            }
            j.endArray()
        }
        is TMap<*, *> -> {
            j.beginObject()
            buildMap<Any?, Any?> {
                while (j.peek() != JsonToken.END_OBJECT) {
                    put(key(j, t.keyType), value(j, t.valueType))
                }
            }
            j.endObject()
        }
        is TStructure<*, *> -> {
            val obj = obj(j)
            check(obj.factory.structure.isAssignableTo(t))
        }
        is TBlob<*> -> TODO()
        else -> error(t)
    }
}

fun obj(j: JsonReader): Obj {
    j.beginObject()
    var obj: ObjBuilder<*>? = null
    while (j.peek() != JsonToken.END_OBJECT) {
        when (val name = j.nextName()) {
            "@type" -> obj = ObjModule.modules.type(typeId(j.nextString())).factory.builder()
            "@id" -> {
                check(obj != null) { "@type should be first" }
                (obj as ObjBuilderImpl).result._id = ObjId<Obj>(j.nextInt())
            }
            else -> {
                check(obj != null) { "@type should be first" }
                val structure = obj.factory.structure
                // todo: extensions
                val field = structure.allFields.find { it.name == name } ?: error(name)
                val value = value(j, field.type)
                //obj[field as Field<Obj, Any?>] = value
            }
        }
    }
    j.endObject()

    return obj!!.build()
}

fun typeId(nextString: String): ObjType.Id<Obj, ObjBuilder<Obj>> {
    TODO("Not yet implemented")
}

private fun key(
    j: JsonReader,
    valueType: ValueType<*>
): Any? {
    return if (valueType is TString) j.nextName()
    else value(JsonReader(StringReader(j.nextName())), valueType)
}