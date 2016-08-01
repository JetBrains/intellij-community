class T {

  void f(String[] a) {
    for (String s : a) {
      System.out.println(s);
    }

    if (a.length == 0) {
      System.out.println("no");
    } else {
      System.out.println(a.length);
    }

    if (a.length == 0) {
      System.out.println("no");
    }

    if (a.length == 0) {
    } else {
      System.out.println(a.length);
    }

    for (int i = 0; i < a.length; i++) {
      System.out.println(a[i]);
    }

    int j = 0;
    do {
      System.out.println(a[j++]);
    }
    while (j < a.length);

    int k = 0;
    while (k < a.length) {
      System.out.println(a[k++]);
    }
  }

  void ff(String[] a) {
    if (a.length != 0) {
      for (String arg : a) {
        if (arg.length() > 1) {
          for (int i = 0; i < arg.length(); i++) {
            System.out.println(arg.charAt(i));
          }
        } else {
          System.out.println(0);
        }
      }
    } else {
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
}