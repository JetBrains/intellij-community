// "Replace with 'BAR'" "true"
package test
import test.Enm.FOO

enum class Enm {
    @Deprecated("Replace with BAR", ReplaceWith("BAR"))
    FOO,
    BAR
}

fun test() {
    FOO<caret>
}