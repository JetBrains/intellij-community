package metaAnnotation;

import org.junit.jupiter.api.*;
import org.junit.platform.suite.api.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@DisplayName("Meta-annotation demonstration")
public class MetaAnnotationTest {
    @IntegrationTest void integrationTest() {}

    @Retry void retryTest() {}

    @Retry
    @Tag("integration")
    void combinedTest() {}
}



