class UnaryPlusConfusing {

  void confusing(int x) {
    x =<warning descr="Unary '+' operator">+</warning> 1;
    x = <warning descr="Unary '+' operator">+</warning>(1);
    x = <warning descr="Unary '+' operator">+</warning> ++x;
    x = 1 + <warning descr="Unary '+' operator">+</warning> 1;
    int y =<warning descr="Unary '+' operator">+</warning> x;
    x = <warning descr="Unary '+' operator">+</warning> /*fsdf*/1;
    x = /*fsdf*/ <warning descr="Unary '+' operator">+</warning>1;
  }

  void notConfusing(int x) {
    System.out.println(+x);
    int[] xs = {+1, -3, +2};
  }
}