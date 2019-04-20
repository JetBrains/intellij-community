def <T0 extends java.io.Serializable> void foo(List<T0> a, T0 x) {
  a.add(x)
}

foo([1, 2, 3], 4)

foo(["a", "b", "c"], "d")
