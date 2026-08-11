// PROBLEM: none
// WITH_STDLIB
// ERROR: Unresolved reference: KProperty0
// K2_ERROR: UNRESOLVED_REFERENCE

val a5<caret> = ""

class D {
    fun a6(d: KProperty0<String> = ::a5) {}
}