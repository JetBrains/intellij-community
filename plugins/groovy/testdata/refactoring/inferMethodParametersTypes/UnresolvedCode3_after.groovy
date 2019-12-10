Void bar(Object a, Closure<Void> b) {
  b(a)
}

bar(unresolved) {}