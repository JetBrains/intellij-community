// WITH_STDLIB
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class Foo<T>(val value: T)

@OptIn(ExperimentalContracts::class)
fun <T> Foo<T>.valueAsString(): String? {
    contract {
        // No 'always true' warning: the erased 'is Foo' check the analysis sees is always true,
        // but the reified 'is Foo<String>' check is not, so no constant condition must be reported.
        returnsNotNull() implies (this@valueAsString is Foo<String>)
    }
    return value as? String
}

fun starProjection(f: Any) {
    val b = f !is Foo<*>
    // A star-projection check is erased anyway, so the analysis is still allowed to report it.
    if (<warning descr="Condition 'b || f is Foo<*>' is always true">b || <warning descr="[USELESS_IS_CHECK]">f is Foo<*></warning></warning>) {}
}
