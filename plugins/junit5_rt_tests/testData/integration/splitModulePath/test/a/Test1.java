package a;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

class Test1 {
  @Test
  void emptyTest5() { }

  @Nested
  class MyNested {
    @Test
    void testFoo() { }
  }
}