public class NonDisjunctTypes {
  static class Ex1 extends Exception {}
  static class Ex2 extends Ex1 {}
  static class Ex3 extends Ex1 {}

  public void test() {
    try {
      if(Math.random() > 0.5) {
        throw new Ex1();
      }
      if(Math.random() > 0.5) {
        throw new Ex2();
      }
      if(Math.random() > 0.5) {
        throw new Ex3();
      }
    }
    catch(RuntimeException | Ex1 ex) {
      ex.printStackTrace();
    }
  }
}