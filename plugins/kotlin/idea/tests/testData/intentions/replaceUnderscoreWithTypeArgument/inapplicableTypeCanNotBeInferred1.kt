// IS_APPLICABLE: false
// ERROR: Not enough information to infer type variable T
// ERROR: Not enough information to infer type variable T
// K2_ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.
// K2_ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.

class Some<T>
fun <T> foo(c: Some<T>) {}

fun test() {
    foo(Some<<caret>_>())
}