import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InterfacedOrderTest implements OrderTestInterface {
  @Test
  @Order(1)
  void name() {
  }

  @Override
  public void testOrderFromParent() {
  }

  @Test
  @Override
  public void withoutTestFromParent() {
  }

  @Override
  public void withoutTestAtAll() {
  }

  @Override
  public void defaultFromParentAndImplemented() {
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