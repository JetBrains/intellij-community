class C {
  void m1(int i) {  // ok
    if (i == 0) System.out.println("zero"); else if (i == 1) System.out.println("one"); else System.out.println("many");
  }

  void m1(char c) {  // ok
    if (c == '0') System.out.println("zero"); else if (c == '1') System.out.println("one"); else System.out.println("many");
  }

  void m1(byte i) {  // ok
    if (i == (byte)0) System.out.println("zero"); else if (i == (byte)1) System.out.println("one"); else System.out.println("many");
  }

  void m1(int i) {  // bad, long literals
    if (i == 0L) System.out.println("zero"); else if (i == 1L) System.out.println("one"); else System.out.println("many");
  }

  void m2(long l) {  // bad, long expression
    if (l == 0) System.out.println("zero"); else if (l == 1) System.out.println("one"); else System.out.println("many");
  }
}