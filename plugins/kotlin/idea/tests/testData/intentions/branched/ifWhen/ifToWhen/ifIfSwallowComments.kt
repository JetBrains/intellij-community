// IGNORE_K1
enum class E {
    A, B, C, D
}

fun main() {
    val k = E.A
    myFun(k)

}

fun myFun(kind: E) {
    // comment0
    if<caret> (kind == E.A) return // also comment
    // comment
    if (kind == E.B) return // also comment
    // comment2
    if (kind == E.C) return
    else println("else")
}
