// PROBLEM: none
// LANGUAGE_VERSION: 1.7
data class TestHolder<T>(
    val additionalData: T
)

fun <T> t1(block: (TestHolder<T>) -> Unit) { }

fun t2(t: String) { }

fun String.foo() = ""

val test = t1<String><caret> {
    val foo = it.additionalData.foo()
    t2(it.additionalData)
}