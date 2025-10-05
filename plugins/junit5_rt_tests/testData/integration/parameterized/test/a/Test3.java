package a;

public class Test3 extends BaseTestCase {
  public Test3(int i) {
    super(i);
  }

  public void simple() throws Exception {
    System.out.println(getClass().getName() + "[" + myField + "]");
  }
}