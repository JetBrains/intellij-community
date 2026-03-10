class TestDoubleCompoundXor {

  long instanceCount = 0;

  int iFld = -39966;

  void mainTest() {

    int i21 = 1;
    int i22 = 1;

    do {

      instanceCount += i21 ^ instanceCount;

      for (i22 = i21; i22 < 68; i22++) {

        switch ((i21 % 9) * 5 + 63) {
          case 98:
            instanceCount += iFld;
            System.out.println("instanceCount 2:" + instanceCount);

        }

      }

    }

    while (++i21 < 373);

    System.out.println("Test.instanceCount finish = " + instanceCount);

  }

  public static void main(String[] strArr) {

    TestDoubleCompoundXor main = new TestDoubleCompoundXor();
    main.mainTest();

  }
}
