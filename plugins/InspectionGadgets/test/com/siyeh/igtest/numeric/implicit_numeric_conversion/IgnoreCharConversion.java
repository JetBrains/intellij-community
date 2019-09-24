class IgnoreCharConversion {

  void m(int i) {
    char a = 'a';
    i = a;
    i += a;
    a += 1;
    byte b = <warning descr="Implicit numeric conversion of '1' from 'int' to 'byte'">1</warning>;
    a += <warning descr="Implicit numeric conversion of 'b' from 'byte' to 'int'"><caret>b</warning>;
  }
}