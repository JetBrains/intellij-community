// "Introduce import alias" "false"
// WITH_STDLIB
// ERROR: Overload resolution ambiguity: <br>public inline fun <T> Iterable<TypeVariable(T)>.forEach(action: (TypeVariable(T)) -> Unit): Unit defined in kotlin.collections<br>public inline fun <K, V> Map<out TypeVariable(K), TypeVariable(V)>.forEach(action: (Map.Entry<TypeVariable(K), TypeVariable(V)>) -> Unit): Unit defined in kotlin.collections
// ERROR: Unresolved reference: a

fun foo() {
    a.b.<caret>forEach { }
}