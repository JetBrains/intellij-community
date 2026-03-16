// WITH_STDLIB
// AFTER-WARNING: Enum argument can be null in Java, but exhaustive when contains no null branch
fun test(outer: Outer) {
    i<caret>f (outer.inner.type == Outer.Inner.Type.A) {
        println("A")
    } else if (outer.inner.type == Outer.Inner.Type.B) {
        println("B")
    } else if (outer.inner.type == Outer.Inner.Type.C) {
        println("C")
    }
}
