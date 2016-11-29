import org.junit.jupiter.api.Assertions;

class MyTest {
  {
      Assertions.assertEquals(foo(), foo(), () -> "message");
  }

  Object foo() {return null;}
}