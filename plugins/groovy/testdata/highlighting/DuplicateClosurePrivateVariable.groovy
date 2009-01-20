def foo() {
    { it -> def x = 0 }
    { it -> def x = 0 }  // syntax error: "Variable 'x' is already defined"
  }