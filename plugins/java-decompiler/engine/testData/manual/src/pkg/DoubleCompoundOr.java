class TestDoubleCompoundOr {
  void mainTest() {
    int i = 0, i12 = 0;
    i += -125;
    i += i12 | i;
    i += i12 | i;
    System.out.println("i1:"+i);
    int f1 = 1;
    do {
      for (i12 = 1; i12 < 6;++i12) {
        i += i12 | i;
        System.out.println("i:"+i);
      }
    }
    while (++f1 < 299);

    System.out.println("result: " + i);
  }
  public static void main(String[] strArr) {
    TestDoubleCompoundOr _instance = new TestDoubleCompoundOr();
    _instance.mainTest();
  }
}