enum class EnumClass { VAL }

fun foo() {
    enumValues<caret><EnumClass>()[0] = EnumClass.VAL
}