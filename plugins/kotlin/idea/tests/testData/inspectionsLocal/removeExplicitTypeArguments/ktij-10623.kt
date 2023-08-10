// FIX: Remove explicit type arguments
// WITH_STDLIB

interface A

class B : A

fun test(l: List<Int>, a: A?) {
    val b: List<A> = l.mapTo(ArrayList<caret><A>(12)) {
        a ?: B()
    }
}