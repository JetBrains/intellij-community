import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InheritedOrderTest extends BaseOrderTest {
  @Test
  @Order(2)
  void name() {
  }

  @Test
  @Order(3)
  void notFromBase() {
  }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BaseOrderTest {
  @Test
  @Order(1)
  void fromBase() {
  }

  @Test
  @Order(4)
  void onlyAtBase() {
  }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InheritedFromAbstractOrderTest extends AbstractOrderTest {
  @Test
  @Order(1)
  @Override
  void fromBase() {
  }
}

abstract class AbstractOrderTest {
  @Test
  @Order(1)
  void fromBase() {
  }
}
