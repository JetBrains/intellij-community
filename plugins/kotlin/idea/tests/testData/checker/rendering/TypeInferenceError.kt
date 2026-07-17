fun <K, V> testMutableMapEntry(<warning descr="[UNUSED_PARAMETER]">map</warning>: MutableMap<K, V>, <warning descr="[UNUSED_PARAMETER]">k1</warning>: K, <warning descr="[UNUSED_PARAMETER]">v</warning>: V) {
}

fun foo() {
    testMutableMapEntry(hashMap(1 to 'a'), <error descr="[NO_VALUE_FOR_PARAMETER]">'b')</error>
}

//extract from library
fun <K, V> hashMap(<warning descr="[UNUSED_PARAMETER]">p</warning>: Pair<K, V>): MutableMap<K, V> {<error descr="[NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY]">}</error>
infix fun <K, V> K.to(<warning descr="[UNUSED_PARAMETER]">v</warning>: V): Pair<K, V> {<error descr="[NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY]">}</error>
class Pair<K, V> {}