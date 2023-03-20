// "Import extension function 'Int.ext'" "true"
package p

open class Foo {
    fun Int.ext() {}
}

object FooObject : Foo()

fun Int.anotherExt() {
    <caret>ext()
}
