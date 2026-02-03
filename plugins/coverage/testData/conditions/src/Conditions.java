public class Conditions {
  void oneBranch1(int x) {
    if (x == 1) {
      System.out.println("case 1");
    }
    else {
      System.out.println("case 2");
    }
  }

  void oneBranch2(int x) {
    if (x == 1) {
      System.out.println("case 1");
    }
    else {
      System.out.println("case 2");
    }
  }

  void allBranches(int x) {
    if (x == 1) {
      System.out.println("case 1");
    }
    else {
      System.out.println("case 2");
    }
  }

  void singleBranch1(int x) {
    if (x == 1) {
      System.out.println("case 1");
    }
    System.out.println("case 2");
  }

  void singleBranch2(int x) {
    if (x == 1) {
      System.out.println("case 1");
    }
    System.out.println("case 2");
  }

  void empty(int x) {
    if (x == 1) {
      System.out.println("case 1");
    }
    else {
      System.out.println("case 2");
    }
  }

  void and1(boolean a, boolean b) {
    if (a && b) {
      System.out.println("both a and b are true");
    }
    else {
      System.out.println("either a or b is false");
    }
  }

  void and2(boolean a, boolean b) {
    if (a && b) {
      System.out.println("both a and b are true");
    }
    else {
      System.out.println("either a or b is false");
    }
  }

  void and3(boolean a, boolean b) {
    if (a && b) {
      System.out.println("both a and b are true");
    }
    else {
      System.out.println("either a or b is false");
    }
  }

  void fullAnd(boolean a, boolean b) {
    if (a && b) {
      System.out.println("both a and b are true");
    }
    else {
      System.out.println("either a or b is false");
    }
  }

  void or1(boolean a, boolean b) {
    if (a || b) {
      System.out.println("either a or b is true");
    }
    else {
      System.out.println("both a and b are false");
    }
  }

  void or2(boolean a, boolean b) {
    if (a || b) {
      System.out.println("either a or b is true");
    }
    else {
      System.out.println("both a and b are false");
    }
  }

  void or3(boolean a, boolean b) {
    if (a || b) {
      System.out.println("either a or b is true");
    }
    else {
      System.out.println("both a and b are false");
    }
  }

  void fullOr(boolean a, boolean b) {
    if (a || b) {
      System.out.println("either a or b is true");
    }
    else {
      System.out.println("both a and b are false");
    }
  }

  void andAnd0(boolean a, boolean b, boolean c) {
    if (a && b && c) {
      System.out.println("All true");
    }
    else {
      System.out.println("Some one is false");
    }
  }

  void andAnd1(boolean a, boolean b, boolean c) {
    if (a && b && c) {
      System.out.println("All true");
    }
    else {
      System.out.println("Some one is false");
    }
  }

  void andAnd2(boolean a, boolean b, boolean c) {
    if (a && b && c) {
      System.out.println("All true");
    }
    else {
      System.out.println("Some one is false");
    }
  }

  void andAnd3(boolean a, boolean b, boolean c) {
    if (a && b && c) {
      System.out.println("All true");
    }
    else {
      System.out.println("Some one is false");
    }
  }

  boolean negation(boolean a) {
    return !a;
  }

  // condition is eliminated as the bytecode is the same as in the previous method
  boolean manualNegation(boolean a) {
    return a == false ? true : false;
  }

  boolean andWithoutIf(boolean a, boolean b) {
    return a && b;
  }

  boolean orWithoutIf(boolean a, boolean b) {
    return a || b;
  }

  void forCycle(int n) {
    for (int i = 0; i < n; i++) {
      System.out.println(i);
    }
  }

  void forEachCycle() {
    String[] elements = new String[]{"a", "b", "c"};

    for (String e : elements) {
      System.out.println(e);
    }
  }

  void whileCycle(int n) {
    int i = 0;
    while (i < n) {
      System.out.println(i);
      i++;
    }
  }

  String ternaryOperator1(boolean a) {
    return a ? "1" : "2";
  }

  String ternaryOperator2(boolean a) {
    return a ? "1" : "2";
  }

  String ternaryOperatorFull(boolean a) {
    return a ? "1" : "2";
  }

  String ternaryOr(boolean a, boolean b) {
    return a || b ? "1" : "2";
  }

  void doWhileCycle(int n) {
    int i = 0;
    do {
      System.out.println(i);
    } while (i++ < n);
  }
}
