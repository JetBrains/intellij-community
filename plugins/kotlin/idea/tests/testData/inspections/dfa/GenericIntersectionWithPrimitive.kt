// WITH_STDLIB
interface Field<V : Any> {
    fun read(json: Map<String, Any?>): V?
}

class IntField : Field<Int> {
    override fun read(json: Map<String, Any?>) = (json["n"] as? Number)?.toInt()
}

class LongField : Field<Long> {
    override fun read(json: Map<String, Any?>) = (json["n"] as? Number)?.toLong()
}

class BooleanField : Field<Boolean> {
    override fun read(json: Map<String, Any?>) = json["b"] as? Boolean
}

class CharField : Field<Char> {
    override fun read(json: Map<String, Any?>) = (json["c"] as? String)?.firstOrNull()
}

class TextField : Field<String> {
    override fun read(json: Map<String, Any?>) = json["s"] as? String
}

fun <V : Any> Field<V>.render(json: Map<String, Any?>): String? {
    return when (this) {
        is IntField -> read(json)?.toString()
        is LongField -> read(json)?.toString()
        is BooleanField -> read(json)?.toString()
        is CharField -> read(json)?.toString()
        is TextField -> read(json)?.uppercase()
        else -> null
    }
}

fun <V : Any> Field<V>.isPresent(json: Map<String, Any?>): Boolean {
    if (this is IntField) {
        val value = read(json)
        return value != null
    }
    return false
}

fun <V : Any> Field<V>.isLarge(json: Map<String, Any?>): Boolean {
    if (this is IntField) {
        val value = read(json) ?: return false
        if (value > 5) {
            return <warning descr="Condition 'value > 3' is always true">value > 3</warning>
        }
    }
    return false
}

fun <V> Field<V>.renderBounded(json: Map<String, Any?>): String? where V : Number, V : Comparable<V> {
    return when (this) {
        is IntField -> read(json)?.toString()
        else -> null
    }
}
