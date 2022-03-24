// WITH_STDLIB

package test

typealias Global = String
fun usesGlobal(p: List<Global>) {
    p.map { <caret>it.uppercase() }
}