package metaAnnotation;

import org.junit.jupiter.api.*;
import org.junit.platform.suite.api.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@SuiteDisplayName("Metaannotated Test Suite")
@SelectPackages("metaAnnotation")
@IncludeClassNamePatterns(".*Test")
@IncludeTags("integration")
@Suite
@interface IntegrationSuite {
}

