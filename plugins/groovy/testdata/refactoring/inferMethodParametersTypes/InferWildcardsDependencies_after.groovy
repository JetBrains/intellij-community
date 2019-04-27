def <Y0, U0 extends Serializable & Comparable<Y0>, U1 extends Serializable & Comparable<Y0>, W0 extends Serializable & Comparable<U1>, X0 extends Serializable & Comparable<U1>, T1 extends Serializable & Comparable<Y0>, V1 extends X0> void foo(List<X0> a, X0 x) {
  a.add(x)
}

foo([1, 2, 3], 4)

foo(["a", "b", "c"], "d")
