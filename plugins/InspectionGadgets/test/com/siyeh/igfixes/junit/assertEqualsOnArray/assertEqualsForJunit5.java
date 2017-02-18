class MyTest {
  void myTest(){
    Object[] a = {};
    Object[] e = {""};
    org.junit.jupiter.api.Assertions.assert<caret>Equals(a, e, "message");
  }
}