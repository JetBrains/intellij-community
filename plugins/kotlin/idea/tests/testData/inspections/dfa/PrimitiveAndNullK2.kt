// WITH_STDLIB
fun main() {
    val a = 3
    val b = 5
    // K2 difference: SENSELESS_COMPARISON warning text differs
    if (<warning descr="Condition 'a != null && b != null' is always true"><warning descr="[SENSELESS_COMPARISON] Condition is always 'true'.">a != null</warning> && <warning descr="[SENSELESS_COMPARISON] Condition is always 'true'.">b != null</warning></warning>) {
        println()
    } else {
        println()
    }
}