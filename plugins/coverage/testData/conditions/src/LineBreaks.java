public class LineBreaks {
  void doSwitch(int x) {
    switch
    (x) {
      case 1:
        System.out.println();
        break;
      case 2:
        System.out.println();
        break;
    }
  }

  void doIf(int x) {
    if
    (x == 1)
      System.out.println("case 1");
  }

  boolean f(boolean x) {
    return x;
  }

  void ifVariable(boolean a, boolean b, boolean c) {
    if (a
      && b
      && c)
      System.out.println("case 2");
  }

  void ifMethods(boolean a, boolean b, boolean c) {
    if (f(a)
      && f(b)
      && f(c))
      System.out.println("case 2");
  }

  boolean andWithoutIfVariables(boolean a, boolean b, boolean c) {
    return a
      && b
      && c;
  }

  boolean andWithoutIfMethods(boolean a, boolean b, boolean c) {
    return f(a)
      && f(b)
      && f(c);
  }

  void forCycle(int n) {
    for (
      int i = 0;
      i
        <
        n;
      i++
    ) {
      System.out.println(i);
    }
  }

  void forEachCycle(String[] elements) {
    for (String e :
      elements) {
      System.out.println(e);
    }
  }

  void whileCycle(int n) {
    int i = 0;
    while
    (
      i
        <
        n
    ) {
      System.out.println(i);
      i++;
    }
  }

  void doWhileCycle(int n) {
    int i = 0;
    do {
      System.out.println(i);
    } while
    (
      i++ < n);
  }


  String ternaryOperator(boolean a) {
    return
      a
        ?
        "1"
        :
        "2";
  }
}
