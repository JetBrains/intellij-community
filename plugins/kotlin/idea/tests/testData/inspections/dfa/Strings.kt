// WITH_STDLIB
enum class MyEnum {A, B}

fun enumName(x: MyEnum, s: String) {
    if (x.name == "A") {
        if (x.name == s) {
            if (<warning descr="Condition 's == \"A\"' is always true">s == "A"</warning>) {}
        }
    }
}
fun concat(x : Int) {
    val data = "Value = " + x
    if (<warning descr="Condition 'data.length > 5' is always true">data.length > 5</warning>) { }
}
fun String.removeSuffix(c: Char): String {
    val n = this.length
    if (n > 1) return this.substring(0, n - 2)
    else if (this[<warning descr="Index is always out of bounds">n - 2</warning>] == c) return this.substring(0, n - 2)
    else return this
}