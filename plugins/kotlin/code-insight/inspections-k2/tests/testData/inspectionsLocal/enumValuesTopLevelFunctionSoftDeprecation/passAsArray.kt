enum class MyEnum {
    A, B, C
}

fun test(arr: Array<out Any>) {
    println(arr.size)
}

fun main() {
    test(enumValues<caret><MyEnum>())
}