def <V0 extends java.util.List<? extends T0>, T0 extends X1> void foo(List<V0> a, V0 b) {
  a.add(b)
}

interface X1{}
class A implements X1{}
class B implements X1{}

void m(A a, B b) {
  foo([[a]], [a])
  foo([[b]], [b])
}