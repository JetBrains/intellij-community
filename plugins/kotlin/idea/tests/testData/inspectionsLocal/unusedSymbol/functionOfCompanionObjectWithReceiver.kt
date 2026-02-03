// PROBLEM: none
// WITH_STDLIB
class My123() {
    companion object {
        fun impCost<caret>() = 42
    }
}

fun tete() {
    println(My123.impCost())
}