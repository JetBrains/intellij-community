static void foo(self, closure) {
  closure(self, 0)
}


foo([1, 2, 3], {list, ind -> println(list[0])})