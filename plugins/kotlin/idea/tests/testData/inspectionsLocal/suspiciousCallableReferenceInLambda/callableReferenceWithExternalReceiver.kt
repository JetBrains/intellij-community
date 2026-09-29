// FIX: none
// WITH_STDLIB

class C { fun method(): String = "" }

fun caller(c: C, l: List<C>) {
    l.map { <caret>c::method }
}