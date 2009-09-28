public class Test2 extends Test1 {
  public void method(int anObject) {
    System.out.println(this.anObject);
  }
}

public class Usage {
  {
    Test t = new Test2();
    t.method(1 + 2);
  }
}