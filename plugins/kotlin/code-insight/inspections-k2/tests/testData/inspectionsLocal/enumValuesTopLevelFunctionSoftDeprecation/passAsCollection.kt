enum class MyEnum {
    A, B, C
}

fun test(arr: Collection<Any>) {
    println(arr.size)
}

fun main() {
    test(enumValues<caret><MyEnum>().toList())
}