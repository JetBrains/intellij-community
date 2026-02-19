// FIR_IDENTICAL
// IGNORE_K1
// FILE: JTestRootUsage.java
@TestRoot()
public class JTestRootUsage {

    @TestAnn2 String s;

}

class Test {
    @MyNullable
    String foo(CharSequence cs) {
        return cs.toString();
    }
}

// FILE: annotations.kt
import java.lang.annotation.ElementType.METHOD
import java.lang.annotation.ElementType.PACKAGE

@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FILE,
)
@Suppress("DEPRECATED_JAVA_ANNOTATION")
@java.lang.annotation.Target(
    METHOD,
    PACKAGE,
)
annotation class MyNullable

@Target(AnnotationTarget.CLASS)
annotation class TestRoot

@Target(AnnotationTarget.FIELD)
annotation class TestAnn2
