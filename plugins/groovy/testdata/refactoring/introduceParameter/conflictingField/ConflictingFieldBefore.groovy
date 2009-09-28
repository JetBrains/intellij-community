public class Test2 extends Test1 {
  public void method() {
    System.out.println(anObject);
  }
}

public class Usage {
  {
    Test t = new Test2();
    t.method();
  }
}