// COMPILER_ARGUMENTS: -Xcollection-literals

class MyCollection<T> {
    companion object { operator fun <T> of(vararg elements: T): MyCollection<T> = TODO() }
}

val x = (MyCollection<caret>.of(1, 2, 3))