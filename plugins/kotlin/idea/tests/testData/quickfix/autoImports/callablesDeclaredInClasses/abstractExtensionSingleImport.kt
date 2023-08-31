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

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
/* IGNORE_K2 */