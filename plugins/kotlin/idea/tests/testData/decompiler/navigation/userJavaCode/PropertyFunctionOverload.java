import testData.libraries.*;

class TestOverload {
  void test() {
    new MyTest().newBuilder().cache("");
    String s = new MyTest().newBuilder().m();
    String s2 = new MyTest().newBuilder().getN();
    new MyTest().newBuilder().o();
  }
}
