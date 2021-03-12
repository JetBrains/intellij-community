import org.junit.jupiter.api.*;

class <warning descr="Test class has some methods with @Order but is without @TestMethodOrder">InheritedOrderTest</warning> extends BaseOrderTest {
  @Test
  @Order(2)
  void <warning descr="Test method with @Order inside class without @TestMethodOrder">name</warning>() {
  }

  @Test
  @Order(3)
  void <warning descr="Test method with @Order inside class without @TestMethodOrder">notFromBase</warning>() {
  }
}

class <warning descr="Test class has some methods with @Order but is without @TestMethodOrder">BaseOrderTest</warning> {
  @Test
  @Order(1)
  void <warning descr="Test method with @Order inside class without @TestMethodOrder">fromBase</warning>() {
  }

  @Test
  @Order(4)
  void <warning descr="Test method with @Order inside class without @TestMethodOrder">onlyAtBase</warning>() {
  }
}

class <warning descr="Test class has some methods with @Order but is without @TestMethodOrder">InheritedFromAbstractOrderTest</warning> extends AbstractOrderTest {
  @Test
  @Order(1)
  @Override
  void <warning descr="Test method with @Order inside class without @TestMethodOrder">fromBase</warning>() {
  }
}

abstract class AbstractOrderTest {
  @Test
  @Order(1)
  void fromBase() {
  }
}
