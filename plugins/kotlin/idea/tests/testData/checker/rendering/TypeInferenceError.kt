fun <K, V> testMutableMapEntry(<warning descr="[UNUSED_PARAMETER] Parameter 'map' is never used">map</warning>: MutableMap<K, V>, <warning descr="[UNUSED_PARAMETER] Parameter 'k1' is never used">k1</warning>: K, <warning descr="[UNUSED_PARAMETER] Parameter 'v' is never used">v</warning>: V) {
}

fun foo() {
    testMutableMapEntry(hashMap(1 to 'a'), <error descr="[NO_VALUE_FOR_PARAMETER] No value passed for parameter 'v'">'b')</error>
}

//extract from library
fun <K, V> hashMap(<warning descr="[UNUSED_PARAMETER] Parameter 'p' is never used">p</warning>: Pair<K, V>): MutableMap<K, V> {<error descr="[NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY] A 'return' expression required in a function with a block body ('{...}')">}</error>
infix fun <K, V> K.to(<warning descr="[UNUSED_PARAMETER] Parameter 'v' is never used">v</warning>: V): Pair<K, V> {<error descr="[NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY] A 'return' expression required in a function with a block body ('{...}')">}</error>
class Pair<K, V> {}