import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestsOnlyAtBaseInterfaceTest implements TestsOnlyAtBaseInterface {
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestsOnlyAtBaseClassTest extends TestsOnlyAtBaseClass {
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestsOnlyAtBaseInterfaceWithMethodOrderTest implements TestsOnlyAtBaseInterface {
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestsOnlyAtBaseClassWithMethodOrderTest extends TestsOnlyAtBaseClass {
}

@TestMethodOrder(MyOrderer.class)
class TestsOnlyAtBaseInterfaceWithMethodOrderAndValidTest implements TestsOnlyAtBaseInterface {
}

@TestMethodOrder(MyOrderer.class)
class TestsOnlyAtBaseClassWithMethodOrderAndValidTest extends TestsOnlyAtBaseClass {
}

abstract class TestsOnlyAtBaseClass {
  @Test
  @Order(1)
  void notInChild() {
  }
}

interface TestsOnlyAtBaseInterface {
  @Test
  @Order(1)
  default void notInChild() {
  }
}
