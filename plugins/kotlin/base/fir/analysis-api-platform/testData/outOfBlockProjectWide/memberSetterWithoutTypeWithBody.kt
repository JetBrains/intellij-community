class A {
  var x
    get() = 1
    set(value) {
      <caret>
    }
}

// OUT_OF_BLOCK: false
