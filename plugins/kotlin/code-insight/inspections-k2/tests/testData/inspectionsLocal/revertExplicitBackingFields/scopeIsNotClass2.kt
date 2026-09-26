// COMPILER_ARGUMENTS: -Xexplicit-backing-fields

val items: Set<Int>
    field<caret> = mutableSetOf()

fun main() {
    items.forEach(::println)
}