// "Import extension function 'Obj.ext'" "true"
package p

object Obj
typealias StrangeName = Obj

open class Foo {
    fun StrangeName.ext() {}
}

object FooObj : Foo()

fun usage() {
    Obj.<caret>ext()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix