void foo(List<? extends Exception> a) {
  Exception x = a[0]
}

def bar(List<? extends IOException> l, List<? extends Exception> l2) {
  foo l
  foo l2
}
