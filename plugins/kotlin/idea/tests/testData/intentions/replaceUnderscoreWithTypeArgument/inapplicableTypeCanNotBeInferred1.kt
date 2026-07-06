// IS_APPLICABLE: false
// ERROR: Not enough information to infer type variable T
// ERROR: Not enough information to infer type variable T
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE

class Some<T>
fun <T> foo(c: Some<T>) {}

fun test() {
    foo(Some<<caret>_>())
}