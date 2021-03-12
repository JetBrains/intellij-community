import org.junit.jupiter.api.*;

class <warning descr="Test class has some methods with @Order but is without @TestMethodOrder">TestsOnlyAtBaseInterfaceTest</warning> implements TestsOnlyAtBaseInterface {
}

class <warning descr="Test class has some methods with @Order but is without @TestMethodOrder">TestsOnlyAtBaseClassTest</warning> extends TestsOnlyAtBaseClass {
}

@TestMethodOrder(MethodOrderer.Alphanumeric.class)
class <warning descr="Test class has some methods with @Order and it has @TestMethodOrder but without @Order support">TestsOnlyAtBaseInterfaceWithMethodOrderTest</warning> implements TestsOnlyAtBaseInterface {
}

@TestMethodOrder(MethodOrderer.Random.class)
class <warning descr="Test class has some methods with @Order and it has @TestMethodOrder but without @Order support">TestsOnlyAtBaseClassWithMethodOrderTest</warning> extends TestsOnlyAtBaseClass {
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
