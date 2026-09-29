// FIX: none
// WITH_STDLIB

class C(val x: Int)

fun caller(c: C) {
    listOf(C(1)).map {<caret> c::x }
}
