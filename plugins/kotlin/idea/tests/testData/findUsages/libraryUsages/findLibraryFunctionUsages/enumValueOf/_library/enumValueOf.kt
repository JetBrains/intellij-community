package library
enum class MyEnum {
    A, B
}

fun test() {
    MyEnum.valueOf("A")
}