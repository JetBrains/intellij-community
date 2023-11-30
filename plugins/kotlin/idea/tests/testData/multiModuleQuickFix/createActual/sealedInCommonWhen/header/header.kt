// "Add remaining branches" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO
// IGNORE_K2

sealed class CommonSealedClass()
class SInheritor1 : CommonSealedClass()
class SInheritor2 : CommonSealedClass()

fun hello(c: CommonSealedClass): Int = <caret>when(c) {

}