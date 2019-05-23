def <T1 extends java.util.List<? extends X0>, X0 extends Y1, Y1> void foo(List<T1> a, T1 b) {
  a.add(b)
}


foo([[1]], [1])
foo([['s']], ['s'])
