// FIR_IDENTICAL
// FIR_COMPARISON
// See KTIJ-24083 - Exception on adding a type parameter receiver to a function
fun <F, B> F.<caret>util(): B = TODO()

// NUMBER: 0
