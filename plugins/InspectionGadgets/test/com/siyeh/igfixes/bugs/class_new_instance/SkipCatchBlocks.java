class No {
  void f(Class<?> c) {
    try {
      c.<caret>newInstance();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
}