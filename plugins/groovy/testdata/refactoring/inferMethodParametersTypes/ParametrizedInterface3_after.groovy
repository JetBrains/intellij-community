def <Y0 extends java.util.List<? extends W0>, W0 extends X1, X1> void foo(List<Y0> a, Y0 b) {
  a.add(b)
}


foo([[1]], [1])
foo([['s']], ['s'])
