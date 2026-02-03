// "Create type parameter 'T' in property 'a'" "false"
// ERROR: Unresolved reference: T
class Test {
    val a: <caret>T? = null
}