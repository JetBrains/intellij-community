// WITH_STDLIB
// PROBLEM: none
class AnyType
fun test(a: (p<caret>aram: AnyType) -> Unit) {
   a(AnyType())
}
