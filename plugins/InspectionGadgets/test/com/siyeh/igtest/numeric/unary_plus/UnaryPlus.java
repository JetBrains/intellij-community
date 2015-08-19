class UnaryPlus {

  void m(byte b) {}
  void m(int i) {}

  void p() {
    byte b = 1;
    m(+b); // calls different method when unary plus removed.
    System.out.println(<warning descr="Unary '+' operator">+</warning>1);
  }
}