enum class MyEnum {
    A, B, C
}

fun findA() {
    val a : MyEnum? = enumValues<caret><MyEnum>().firstOrNull { it == MyEnum.A }
}