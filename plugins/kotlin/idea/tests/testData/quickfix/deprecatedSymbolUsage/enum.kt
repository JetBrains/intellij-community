// "Replace with 'BAR'" "true"

enum class Enm {
    @Deprecated("Replace with BAR", ReplaceWith("BAR"))
    FOO,
    BAR
}

fun test() {
    Enm.FOO<caret>
}