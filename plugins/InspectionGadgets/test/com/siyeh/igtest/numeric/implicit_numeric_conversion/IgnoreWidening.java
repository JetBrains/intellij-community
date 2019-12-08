class IgnoreWidening {
  void m(int i) {
    long l = i;
    byte b = 2;
  }

  void f() {
    double d = 3.14;
    int i = 42;
    i += <warning descr="Implicit numeric conversion of 'd' from 'double' to 'int'">d</warning>;
  }
}