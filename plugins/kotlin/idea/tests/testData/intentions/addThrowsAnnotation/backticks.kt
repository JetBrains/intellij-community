// WITH_STDLIB
package foo.bar

class `My$Exception` : Exception()

fun test() {
    <caret>throw `My$Exception`()
}