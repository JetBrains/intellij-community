package metaAnnotation;

import org.junit.jupiter.api.*;
import org.junit.platform.suite.api.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@IntegrationSuite
@DisplayName("Integration Test Suite")
class IntegrationTestSuite {

    @Test void testWithoutIntegrationTagTest() {}

    @Nested
    class NestedIntegrationTests {
        @IntegrationTest
        void nestedIntegrationTest() {}
    }

    @IntegrationTest
    void integrationTest() {}
}


