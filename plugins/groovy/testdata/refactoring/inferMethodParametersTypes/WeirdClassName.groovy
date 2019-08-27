class T {
  void x(){}
}

def f<caret>oo(a, b, c) {
  a.add(b)
}

foo([1], 1, null as T)
foo(['q'], 'q', null as T)