public class AnonymousClassJUnit4 {

  @org.junit.Test
  public void test2BiggerThan1() {
    new Object() {
      void foo() {
        2 > 1<caret>
      }
    }
  }
}