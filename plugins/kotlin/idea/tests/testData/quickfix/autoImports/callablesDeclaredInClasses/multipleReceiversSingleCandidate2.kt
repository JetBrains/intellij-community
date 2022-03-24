// "Import" "true"
// WITH_STDLIB
package p

class A
class B
class C

open class AExt {
    fun A.extension() {}
}

open class BExt {
    fun B.extension() {}
}

open class CExt {
    fun C.extension() {}
}

object AExtObject : AExt()
object BExtObject : BExt()
object CExtObject : CExt()

fun usage(a: A, b: B, c: C) {
    a.run { b.run { c.run { a.<caret>extension() } } }
}
