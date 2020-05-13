enum E {
  W, L, M;

  E getCurrent(boolean b1, boolean b2) {
    if (b1) {
        if (b2) return W;
        return M;
    }
    return b2 ? W : L;
  }
}