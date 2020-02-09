import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderWithoutTestMethodOrderAnnotationTest {
  @Test
  @Order(1)
  void name() {
  }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderWithAlphanumericOrdererTest {
  @Test
  @Order(1)
  void name() {
  }
}

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderWithRandomOrdererTest {
  @Test
  @Order(1)
  void name() {
  }
}
