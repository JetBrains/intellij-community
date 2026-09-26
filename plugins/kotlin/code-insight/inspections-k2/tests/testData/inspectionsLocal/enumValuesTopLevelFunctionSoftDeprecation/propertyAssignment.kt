enum class EnumClass

fun foo() {
    val a = enumValues<caret><EnumClass>()
    for (el in a) {}
}