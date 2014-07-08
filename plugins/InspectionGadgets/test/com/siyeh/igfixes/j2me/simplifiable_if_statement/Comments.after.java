class Comments {

  void m(boolean b) {
      boolean c;
      // 1
// 2
      c = b || f();
// 3
// 4
//5
//6
  }

  boolean f() {
    return true;
  }
}