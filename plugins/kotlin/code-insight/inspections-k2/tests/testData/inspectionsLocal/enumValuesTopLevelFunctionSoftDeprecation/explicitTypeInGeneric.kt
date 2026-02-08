enum class EnumClass

fun <T> genericFunction(a: T) {}

fun foo() {
    genericFunction<Array<*>>(enumValues<caret><EnumClass>())
}