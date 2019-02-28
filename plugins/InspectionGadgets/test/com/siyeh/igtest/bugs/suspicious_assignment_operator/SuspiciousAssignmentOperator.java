class Test {

  void test(int i) {
    int j = 42;
    <warning descr="'2/3' is evaluated before assignment">j *= 2/3</warning>;
    <warning descr="'3/2' is evaluated before assignment">j /= 3/2</warning>;
    j *= i/2;
  }
}