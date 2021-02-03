import org.junit.jupiter.api.Assertions;

class MyTest {
  {
      Assertions.assert<caret>True("literal".equals(foo()), () -> "message");
  }

  String foo() {return null;}
}