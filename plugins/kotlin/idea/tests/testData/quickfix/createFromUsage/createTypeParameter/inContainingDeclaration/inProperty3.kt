// "Create type parameter 'T' in property 'a'" "false"
// ACTION: Create annotation 'T'
// ACTION: Create class 'T'
// ACTION: Create enum 'T'
// ACTION: Create interface 'T'
// ACTION: Enable option 'Property types' for 'Types' inlay hints
// ERROR: Unresolved reference: T
val a = fun() {
    val b: T<caret>
}