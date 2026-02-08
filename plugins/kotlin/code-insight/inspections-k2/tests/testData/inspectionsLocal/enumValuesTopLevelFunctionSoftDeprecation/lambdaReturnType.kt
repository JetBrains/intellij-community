enum class EnumClass

fun test() {
    val res: Array<EnumClass> = run { enumValues<caret><EnumClass>() })
}