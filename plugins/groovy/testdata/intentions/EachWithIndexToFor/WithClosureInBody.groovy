[].ea<caret>chWithIndex { int val, int idx ->
  if (val == 2) {
    println 2
  }
  if (val == 3) {
    println { String s ->
      println s
    }
  }
}