Object foo(ArrayList<? extends A> a) {
  a[0].bar()
}

class A{ void bar(){} }
class B extends A{}
class C extends A{}

foo(new ArrayList<B>())
foo(new ArrayList<C>())