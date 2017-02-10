class MyTest {
  void myTest(){
    Object[] a = {};
    Object[] e = {""};
      org.junit.jupiter.api.Assertions.assertArrayEquals(a, e, "message");
  }
}