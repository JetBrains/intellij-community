class IgnoreWidening {
  void m(int i) {
    long l = i;
    byte b = 2;
  }

  void f() {
    double d = 3.14;
    int i = 42;
    <warning descr="Implicit numeric conversion of result value from 'double' to 'int'">i</warning> += d;
    d += 2;
    d += i;
    byte a = 1;
    byte b = 2;
    <warning descr="Implicit numeric conversion of result value from 'int' to 'byte'"><caret>a</warning> += b;
  }
}