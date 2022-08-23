// "Import extension function 'Int.based'" "true"
package p

abstract class Base1 {
    abstract fun Int.based()
}

abstract class Base2 : Base1() {
    override fun Int.based() {}
}

object Obj : Base2()

fun usage {
    10.<caret>based()
}
