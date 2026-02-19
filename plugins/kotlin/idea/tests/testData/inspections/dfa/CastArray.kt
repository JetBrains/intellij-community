// WITH_STDLIB
fun test(x: Any) {
    if (x is Array<*>) {}
}

enum class MyEnum {A, B}

fun main() {
    @Suppress("UNCHECKED_CAST")
    val vals = MyEnum.values() as Array<Comparable<*>>
    println(vals)
    @Suppress("UNCHECKED_CAST")
    val vals1 = MyEnum.values() <warning descr="Cast will always fail">as</warning> Array<Cloneable>
    println(vals1)
}