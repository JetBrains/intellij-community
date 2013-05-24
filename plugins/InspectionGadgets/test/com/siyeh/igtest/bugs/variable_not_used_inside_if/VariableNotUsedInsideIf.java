



public class VariableNotUsedInsideIf {

  void foo(String s) {
    if (s == null) {
      s = "foo";
    } else {
      System.out.println("not null");
    }
    System.out.println(s);
  }

  void bar(String s) {
    if (s == null) {
      return;
    }
  }

  void bas(String s) {
    if (s == null) {
      s = "";
    }
    System.out.println(s);
  }

  void bat(String s) {
    if (s == null) {
      System.out.println();
    }
    if (s != null) {

    } else {
      
    }
  }

  void money(String s) {
    if ((s == null)) {
      System.out.println();
    }
  }
}