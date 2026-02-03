// IS_APPLICABLE: false
enum class MyEnum {
    A, B, C, D, E
}

fun main() {
    MyEnum.A
    MyEnum.valueOf("A")
    <caret>MyEnum.values()
}
