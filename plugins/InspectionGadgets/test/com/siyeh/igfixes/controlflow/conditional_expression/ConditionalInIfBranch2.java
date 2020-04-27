enum E {
  W, L, M;

  E getCurrent(boolean b1, boolean b2) {
    E e;
    if (b1) e = <caret>b2 ? W : M;
    e = b2 ? W : L;
  }
}