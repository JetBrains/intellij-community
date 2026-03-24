package pkg

class TestIllegalVarName {
  fun m(`this`: String, `enum`: Int): String {
    return `this` + '/' + `enum`
  }
}