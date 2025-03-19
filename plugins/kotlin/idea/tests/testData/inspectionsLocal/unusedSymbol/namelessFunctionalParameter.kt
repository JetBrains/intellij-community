// WITH_STDLIB
// PROBLEM: none
class AnyType
fun test(a: (<caret>AnyType) -> Unit) {
   a(AnyType())
}
