static void foo(self, closure) {
  final Object[] args = new Object[2]
  closure.call(self[0], 0)
}


foo([1, 2, 3], {list, ind -> println(list)})