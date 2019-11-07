class Test {
  void <warning descr="Use of 'enum' as an identifier is not supported in releases after Java 1.4">enum</warning>() {}
  
  void test() {
    int <warning descr="Use of 'enum' as an identifier is not supported in releases after Java 1.4">enum</warning> = 1;
    enum = 2;
    enum();
    new enum();
  }
  
  class <warning descr="Use of 'enum' as an identifier is not supported in releases after Java 1.4">enum</warning> {}
}