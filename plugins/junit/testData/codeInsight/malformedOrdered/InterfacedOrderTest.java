import org.junit.jupiter.api.*;

class <warning descr="Test class has some methods with @Order but is without @TestMethodOrder">InterfacedOrderTest</warning> implements OrderTestInterface {
  @Test
  @Order(1)
  void <warning descr="Test method with @Order inside class without @TestMethodOrder">name</warning>() {
  }

  @Override
  public void <warning descr="Test method with @Order inside class without @TestMethodOrder">testOrderFromParent</warning>() {
  }

  @Test
  @Override
  public void <warning descr="Test method with @Order inside class without @TestMethodOrder">withoutTestFromParent</warning>() {
  }

  @Override
  public void withoutTestAtAll() {
  }

  @Override
  public void <warning descr="Test method with @Order inside class without @TestMethodOrder">defaultFromParentAndImplemented</warning>() {
  }
}

interface OrderTestInterface {
  @Test
  @Order(1)
  void testOrderFromParent();

  @Order(2)
  void withoutTestFromParent();

  @Order(3)
  void withoutTestAtAll();

  @Test
  @Order(4)
  default void defaultFromParent() {
  }

  @Test
  @Order(5)
  default void defaultFromParentAndImplemented() {
  }
}