// IS_APPLICABLE: false
enum class MyEnum {
    A, B, C, D, E
}

fun main() {
    MyEnum.A
    <caret>MyEnum.valueOf("A")
    MyEnum.values()
}
