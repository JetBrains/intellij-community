class C {
  List<Integer> l = new ArrayList<>()

  C<caret>(a) {
    l.add(a)
  }
}

new C(2)