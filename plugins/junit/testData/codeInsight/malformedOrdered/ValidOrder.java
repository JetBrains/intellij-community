import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderTest {
  @Test
  @Order(1)
  void name() {
  }
}

@TestMethodOrder(MyOrderer.class)
class OrderWithCustomOrdererTest {
  @Test
  @Order(1)
  void name() {
  }
}

class ParentTest extends BaseOrderTest {
  @Test
  @Order(2)
  void fromBase() {
  }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BaseOrderTest {
  @Test
  @Order(1)
  void fromBase() {
  }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InheritedFromAbstractOrderTest1 extends AbstractOrderTest1 {
  @Test
  @Order(1)
  @Override
  void fromBase() {
  }
}

abstract class AbstractOrderTest1 {
  @Test
  @Order(1)
  void fromBase() {
  }
}

class InheritedFromAbstractOrderTest2 extends AbstractOrderTest2 {
  @Test
  @Order(1)
  @Override
  void fromBase() {
  }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AbstractOrderTest2 {
  @Test
  @Order(1)
  void fromBase() {
  }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InheritedFromInterfaceOrderTest1 implements InterfaceOrderTest1 {
  @Test
  @Order(1)
  @Override
  public void fromBase() {
  }
}

interface InterfaceOrderTest1 {
  @Test
  @Order(1)
  void fromBase();
}

class InheritedFromInterfaceOrderTest2 implements InterfaceOrderTest2 {
  @Test
  @Order(1)
  @Override
  public void fromBase() {
  }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
interface InterfaceOrderTest2 {
  @Test
  @Order(1)
  void fromBase();
}
