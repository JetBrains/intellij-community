enum class EnumClass { VAL }

fun foo() {
    enumValues<caret><EnumClass>() == arrayOf(EnumClass.VAL)
}