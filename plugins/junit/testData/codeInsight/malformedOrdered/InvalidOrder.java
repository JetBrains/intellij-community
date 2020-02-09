import org.junit.jupiter.api.*;

class <warning descr="Test class has some methods with @Order but is without @TestMethodOrder">OrderWithoutTestMethodOrderAnnotationTest</warning> {
  @Test
  @Order(1)
  void <warning descr="Test method with @Order inside class without @TestMethodOrder">name</warning>() {
  }
}

@TestMethodOrder(MethodOrderer.Alphanumeric.class)
class <warning descr="Test class has some methods with @Order and it has @TestMethodOrder but without @Order support">OrderWithAlphanumericOrdererTest</warning> {
  @Test
  @Order(1)
  void <warning descr="Test method with @Order inside class with @TestMethodOrder but without @Order support">name</warning>() {
  }
}

@TestMethodOrder(MethodOrderer.Random.class)
class <warning descr="Test class has some methods with @Order and it has @TestMethodOrder but without @Order support">OrderWithRandomOrdererTest</warning> {
  @Test
  @Order(1)
  void <warning descr="Test method with @Order inside class with @TestMethodOrder but without @Order support">name</warning>() {
  }
}
