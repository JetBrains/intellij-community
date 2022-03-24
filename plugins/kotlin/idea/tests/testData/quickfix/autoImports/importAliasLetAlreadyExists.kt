// "Import" "true"
// WITH_STDLIB

import kotlin.let as let1

fun main() {
    1.<caret>let {
        println(it)
    }
}