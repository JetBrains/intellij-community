// "Remove @JvmField annotation" "true"
// WITH_STDLIB
class Foo {
    <caret>@JvmField private val bar = 0
}