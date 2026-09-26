// "Create type parameter 'T' in property 'a'" "false"
// ERROR: Unresolved reference: T
val a = fun() {
    val b: T<caret>
}