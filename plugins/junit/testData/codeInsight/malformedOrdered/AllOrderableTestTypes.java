import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import java.util.Collections;

class <warning descr="Test class has some methods with @Order but is without @TestMethodOrder">AllOrderableTestTypes</warning> {
    @Test
    @Order(1)
    void <warning descr="Test method with @Order inside class without @TestMethodOrder">test</warning>() {
    }

    @RepeatedTest(100)
    @Order(2)
    void <warning descr="Test method with @Order inside class without @TestMethodOrder">repeatedTest</warning>() {
    }

    @ParameterizedTest
    @Order(3)
    void <warning descr="Test method with @Order inside class without @TestMethodOrder">parameterizedTest</warning>(int number) {
    }

    @TestFactory
    @Order(4)
    void <warning descr="Test method with @Order inside class without @TestMethodOrder">testFactory</warning>() {
    }

    @TestTemplate
    @Order(5)
    void <warning descr="Test method with @Order inside class without @TestMethodOrder">testTemplate</warning>() {
    }
}