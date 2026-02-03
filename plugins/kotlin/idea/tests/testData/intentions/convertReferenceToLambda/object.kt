// IS_APPLICABLE: true
// WITH_STDLIB
val list = listOf(1, 2, 3).map(<caret>Utils::foo)

object Utils {
    fun foo(x: Int) = x
}