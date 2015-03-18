class Bar {
    boolean isFo<caret>cused() { }
}

def boo(Map args, p) {
  println p.focused
  println p.isFocused()

  // not usage candidates
  println focused
  println isFocused()
  println "a".focused
  println "a".isFocused()

  println args.focused
}

