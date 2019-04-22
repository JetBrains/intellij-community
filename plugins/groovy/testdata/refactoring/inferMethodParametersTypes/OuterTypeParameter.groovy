def void foo(ts, us) {
  ts.add(us);
}

def <T> void m() {
  List<T> ts = null;
  T t = null;
  foo(ts, t)
}