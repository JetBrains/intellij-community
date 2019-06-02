def <U0 extends C<T0>, T0> void foo(C<U0> a, U0 b) {
  a.consume(b)
  b.consume(a.produce().produce())
}

void m(C<C<A>> cca, C<C<B>> ccb, C<A> ca, C<B> cb) {
  foo(cca, ca)
  foo(ccb, cb)
}

class C<T> {
  void consume(T t) {}
  T produce() {return null}
}

class A{}
class B{}