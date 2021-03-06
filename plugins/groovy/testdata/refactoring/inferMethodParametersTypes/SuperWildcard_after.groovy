Object foo(ArrayList<? super B> a) {
  C c = new C()
  a.add(c)
}

class A {}

class B extends A {}

class C extends B {}


def m(A a, B b) {
  foo([a])
  foo([b])
}
