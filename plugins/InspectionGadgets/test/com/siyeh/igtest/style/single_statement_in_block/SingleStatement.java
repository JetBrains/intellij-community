class T {

  void f(String[] a) {
    <warning descr="'for' contains single statement">for</warning> (String s : a) {
      System.out.println(s);
    }

    <warning descr="'if' contains single statement">if</warning> (a.length == 0) {
      System.out.println("no");
    } <warning descr="'else' contains single statement">else</warning> {
      System.out.println(a.length);
    }

    <warning descr="'if' contains single statement">if</warning> (a.length == 0) {
      System.out.println("no");
    }

    if (a.length == 0) {
    } <warning descr="'else' contains single statement">else</warning> {
      System.out.println(a.length);
    }

    <warning descr="'for' contains single statement">for</warning> (int i = 0; i < a.length; i++) {
      System.out.println(a[i]);
    }

    int j = 0;
    <warning descr="'do' contains single statement">do</warning> {
      System.out.println(a[j++]);
    }
    while (j < a.length);

    int k = 0;
    <warning descr="'while' contains single statement">while</warning> (k < a.length) {
      System.out.println(a[k++]);
    }
  }

  void nested(String[] a) {
    <warning descr="'if' contains single statement">if</warning> (a.length != 0) {
      <warning descr="'for' contains single statement">for</warning> (String arg : a) {
        <warning descr="'if' contains single statement">if</warning> (arg.length() > 1) {
          <warning descr="'for' contains single statement">for</warning> (int i = 0; i < arg.length(); i++) {
            System.out.println(arg.charAt(i));
          }
        } <warning descr="'else' contains single statement">else</warning> {
          System.out.println(0);
        }
      }
    } <warning descr="'else' contains single statement">else</warning> {
      System.out.println("no");
    }
  }

  void decl(String[] a) {
    if (a.length == 1) {
      String t = a[0];
    }
    for (int i = 0; i < a.length; i++) {
      String t = a[i];
    }
  }

  void labeled(String[] a) {
    OuterIf:
    <warning descr="'if' contains single statement">if</warning> (a != null) {
      OuterFor:
      <warning descr="'for' contains single statement">for</warning> (String s : a) {
        InnerFor:
        <warning descr="'for' contains single statement">for</warning> (int i = 0; i < s.length(); i++) {
          InnerIf:
          <warning descr="'if' contains single statement">if</warning> (s.charAt(i) == ' ') {
            break OuterFor;
          }
        }
      }
    }
  }

  void danglingElse(Object[] a) {
    if (a != null) {
      if (a.length != 0)
        System.out.println(a[0]);
    }
    else
      System.out.println("null");
  }

  void noDanglingElse(Object[] a) {
    <warning descr="'if' contains single statement">if</warning> (a != null) {
      if (a.length != 0)
        System.out.println(a[0]);
      else
        System.out.println("empty");
    }
    else
      System.out.println("null");
  }

  void danglingElseNestedIfChain(Object[] a) {
    if (a != null) {
      if (a.length != 0)
        if(a[0] != null)
          System.out.println(a[0]);
    }
    else
      System.out.println("null");
  }

  void danglingElseNestedIfElse(Object[] a) {
    if (a != null) {
      if (a.length != 0)
        if (a[0] != null)
          System.out.println(a[0]);
        else
          System.out.println("missing");
    }
    else
      System.out.println("null");
  }

  void noDanglingElseNestedIf(Object[] a) {
    <warning descr="'if' contains single statement">if</warning> (a != null) {
      if (a.length != 0)
        if (a[0] != null)
          System.out.println(a[0]);
        else
          System.out.println("missing");
      else
        System.out.println("empty");
    }
    else
      System.out.println("null");
  }

  void danglingElseWithLoop(Object[] a) {
    if (a != null) {
      for (int i = 0; i < a.length; i++)
        if (a[i] != null)
          System.out.println(a[i]);
    }
    else
      System.out.println("null");
  }

  void noDanglingElseWithLoop(Object[] a) {
    <warning descr="'if' contains single statement">if</warning> (a != null) {
      for (int i = 0; i < a.length; i++)
        if (a[i] != null)
          System.out.println(a[i]);
        else
          System.out.println("missing");
    }
    else
      System.out.println("null");
  }

  public int danglingElseWithTwoLoops(Object[] a, Object o) {
    if (o == null) {
      for (int i = 0; i < a.length; i++)
        if (a[i] == null)
          return i;
    } else
      for (int i = 0; i < a.length; i++)
        if (o.equals(a[i]))
          return i;
    return -1;
  }
}