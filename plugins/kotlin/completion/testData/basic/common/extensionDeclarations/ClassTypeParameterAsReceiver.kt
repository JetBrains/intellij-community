// FIR_IDENTICAL
// FIR_COMPARISON
// See KTIJ-24083 - Exception on adding a type parameter receiver to a function
class F {
  companion object { }
}

class Bar<F> {
    fun F.<caret>
}

// NUMBER: 0
