enum class MyEnum {
    A, B, C, D, E
}

fun main() {
    <caret>MyEnum.A
    MyEnum.valueOf("A")
    MyEnum.values()
}
