// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
import java.io.Serializable

fun main() {
    val x = <caret>object : Serializable {}
}
