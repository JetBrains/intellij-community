// COMPILER_ARGUMENTS: -Xcollection-literals

class MyCollection<T> {
    companion object { operator fun <T> of(vararg elements: T): MyCollection<T> = TODO() }
}

val x: MyCollection<Int> = ([1, 2<caret>, 3])