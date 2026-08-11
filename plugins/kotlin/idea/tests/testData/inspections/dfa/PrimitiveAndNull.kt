// WITH_STDLIB
fun main() {
    val a = 3
    val b = 5
    if (<warning descr="Condition 'a != null && b != null' is always true"><warning descr="[SENSELESS_COMPARISON]">a != null</warning> && <warning descr="[SENSELESS_COMPARISON]">b != null</warning></warning>) {
        println()
    } else {
        println()
    }
}