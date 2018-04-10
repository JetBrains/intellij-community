package com.siyeh.igtest.controlflow.double_negation;

@SuppressWarnings("unused")
class LambdaUnfriendlyOverload<T> {
  interface Consumer<C> {
    void m(C c);
  }
  interface Predicate<P> {
    boolean t(P p);
  }
  void act(Consumer<T> consumer) {}
  void act(Predicate<T> predicate) {}

  void test() {
    act(s -> !!s.equals("abc"));
    act(s -> !(!s.equals("abc")));
  }
}

public class DoubleNegation {

  void negative(boolean b1, boolean b2, boolean b3) {
    boolean r1 = <warning descr="Double negation in '!(b1 != b2)'">!(b1 != b2)</warning>;
    boolean r2 = <warning descr="Double negation in '!!b1'">!!b1</warning>;
    boolean r3 = <warning descr="Double negation in '!b1 != b2'">!b1 != b2</warning>;
    boolean r4 = (<warning descr="Double negation in 'b1 != (b2 != b3)'">b1 != (b2 != b3)</warning>);
    boolean r5 = (<warning descr="Double negation in 'b1 != b2 != b3'">b1 != b2 != b3</warning>);
  }

  void vm(Double a, double b) {
    boolean r = <warning descr="Double negation in '!(a != null)'">!(a != null)</warning> || !(b != Double.NaN != false);
  }
}
