// "Remove @JvmField annotation" "true"
// WITH_RUNTIME
class Foo {
    <caret>@JvmField private val bar = 0
}