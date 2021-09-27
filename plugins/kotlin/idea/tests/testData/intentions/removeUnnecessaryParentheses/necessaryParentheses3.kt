// IS_APPLICABLE: false
// AFTER-WARNING: No cast needed
fun foo(x: Int) : Any {
    return <caret>(x as Int) < 42
}