// FIR_IDENTICAL
// FIR_COMPARISON
// See KTIJ-24083 - Exception on adding a type parameter receiver to a function
class F {
  companion object { }
}

fun <F> F.<caret>

// NUMBER: 0
