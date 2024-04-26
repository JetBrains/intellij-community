// PROBLEM: none
package one

var pfd<caret>: String
    get() = ""
    set(value) {}

fun test() {
    pfd = ""
}