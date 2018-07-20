// "Replace 'if else' with '?:'" "INFORMATION"
class Test {
  interface I {
    int m();
  }
  
  String foo() {
    I i = () -> {
        return true ? 1 : new Integer(2);
    };
    return null;
  }
}