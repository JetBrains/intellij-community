// WITH_STDLIB
// See KT-13411

fun use() {
    B.text.map { <caret>it.toString() }
}