[].ea<caret>ch {
  if (it == 2) {
    println 2
  }
  if (it == 3) {
    println { String s ->
      println s
    }
  }
}