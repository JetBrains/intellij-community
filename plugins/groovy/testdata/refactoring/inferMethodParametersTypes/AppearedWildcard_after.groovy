def <T0 extends java.lang.Number> Object foo(ArrayList<T0> a) {
  a[0].doubleValue()
}

foo(new ArrayList<Integer>())
foo(new ArrayList<Double>())