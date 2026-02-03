interface Foo {
    val origin: Origin
}

sealed class Origin
object Src: Origin()
object Lib: Origin()
object Sdk: Origin()

internal val Foo.bar: Boolean
    get() = <caret>when {
        origin == Src -> false


        origin == Lib || origin == Sdk -> false

        else -> true
    }