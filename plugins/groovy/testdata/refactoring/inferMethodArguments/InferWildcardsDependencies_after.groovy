def void foo(List<Serializable> a, Serializable x) {
  a.add(x)
}

foo([1, 2, 3], 4)

foo(["a", "b", "c"], "d")
