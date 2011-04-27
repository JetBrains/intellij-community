class Bar {
    boolean isFo<caret>cused() { }
}

def boo(Map args, p) {
  println p.focused
  println p.isFocused()

  println args.focused //not a usage
}

