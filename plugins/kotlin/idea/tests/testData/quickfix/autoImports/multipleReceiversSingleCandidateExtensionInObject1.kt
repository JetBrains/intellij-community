// "Import" "true"
// WITH_RUNTIME
package p

class A
class B
class C

object AExtObject {
    fun A.extension() {}
}

object BExtObject {
    fun B.extension() {}
}

object CExtObject {
    fun C.extension() {}
}

fun usage(a: A, b: B, c: C) {
    a.run { b.run { c.<caret>extension() } }
}
