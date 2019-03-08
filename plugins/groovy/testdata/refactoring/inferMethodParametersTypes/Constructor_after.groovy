class C {
  List<Integer> l = new ArrayList<>()

  C<caret>(Integer a) {
    l.add(a)
  }
}

new C(2)