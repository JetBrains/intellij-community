// "Replace 'if else' with '?:'" "INFORMATION"
class Test {
  interface I {
    int m();
  }
  
  String foo() {
    I i = () -> {
      <caret>if (true) {
        return 1;
      }
      return new Integer(2);
    };
    return null;
  }
}