def <T0> void foo(List<T0> ts, T0 us) {
  ts.add(us);
}

def <T> void m() {
  List<T> ts = null;
  T t = null;
  foo(ts, t)
}