// ERROR: Unresolved reference: TODO
package to

class A<K, V>

operator fun <K, V>  A<K, V>.get(key: K): V = TODO()

fun test(map: A<Int, String>) {
    map[1]
}
