import org.junit.jupiter.api.Assertions;

class MyTest {
  {
      Assertions.assert<caret>True(foo().equals(foo()), () -> "message");
  }

  Object foo() {return null;}
}