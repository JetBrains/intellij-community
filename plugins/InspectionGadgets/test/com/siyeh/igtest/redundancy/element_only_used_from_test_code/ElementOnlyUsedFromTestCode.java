package element_only_used_from_test_code;

interface Base {
  void method();
  void method2();
}

class Aardvark {
  public
  Aardvark(Base b) {
    b.method();
  }
}

class Bee implements Base {
  @Override
  public void method() {
  }

  @Override
  public void method2() {

  }
}
public class ElementOnlyUsedFromTestCode {
  Bee b = new Bee();

  @org.junit.Test
  public void testSomething() {
    b.method();
  }

  @org.junit.Test
  public void testSomethingElse() {
    b.method2();
  }
}