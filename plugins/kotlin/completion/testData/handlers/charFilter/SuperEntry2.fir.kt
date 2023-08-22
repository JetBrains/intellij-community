class C : java.io.BufferedReader() {
  override fun hashCode(): Int {
    super<Buf<caret>>.hashCode()
  }
}

// FIR_COMPARISON
// INVOCATION_COUNT: 2
// ELEMENT: BufferedReader
// CHAR: ' '
