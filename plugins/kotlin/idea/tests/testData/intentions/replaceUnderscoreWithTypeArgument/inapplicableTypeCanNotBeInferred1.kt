// IS_APPLICABLE: false
// ERROR: Not enough information to infer type variable T
// ERROR: Not enough information to infer type variable T

class Some<T>
fun <T> foo(c: Some<T>) {}

fun test() {
    foo(Some<<caret>_>())
}