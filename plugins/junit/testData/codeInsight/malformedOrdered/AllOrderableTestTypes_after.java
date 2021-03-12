import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import java.util.Collections;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AllOrderableTestTypes {
    @Test
    @Order(1)
    void test() {
    }

    @RepeatedTest(100)
    @Order(2)
    void repeatedTest() {
    }

    @ParameterizedTest
    @Order(3)
    void parameterizedTest(int number) {
    }

    @TestFactory
    @Order(4)
    void testFactory() {
    }

    @TestTemplate
    @Order(5)
    void testTemplate() {
    }
}