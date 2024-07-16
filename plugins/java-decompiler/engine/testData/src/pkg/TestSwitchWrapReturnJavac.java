class TestSwitchWrapReturnJavac {
  sealed interface S {
    record A() implements S {
    }

    record B() implements S {
    }

    record C() implements S {
    }
  }

  public static void main(String[] args) {
    System.out.println(test(new S.A()));
  }

  private static int test(S a) {
    switch (a) {
      case S.A a1 -> {
        return 1;
      }
      case S.B b -> {
        return 2;
      }
      case S.C c -> {
        return 3;
      }
    }
  }
}
