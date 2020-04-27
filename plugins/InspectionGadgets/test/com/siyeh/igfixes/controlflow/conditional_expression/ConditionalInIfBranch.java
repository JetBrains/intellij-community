enum E {
  W, L, M;

  E getCurrent(boolean b1, boolean b2) {
    if (b1) return <caret>b2 ? W : M;
    return b2 ? W : L;
  }
}