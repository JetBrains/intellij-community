class Usage {
  int m() {
    final T2 t = new T2()
    return t.method(0, t.method(0) + 1);
  }
}