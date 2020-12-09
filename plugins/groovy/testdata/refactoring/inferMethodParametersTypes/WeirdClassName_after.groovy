class T {
  void x(){}
}

def <U> boolean f<caret>oo(ArrayList<U> a, U b, T c) {
  a.add(b)
}

foo([1], 1, null as T)
foo(['q'], 'q', null as T)