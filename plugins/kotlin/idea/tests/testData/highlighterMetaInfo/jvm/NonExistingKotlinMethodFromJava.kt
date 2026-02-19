// FIR_IDENTICAL
// ALLOW_ERRORS
// FILE: JavaClass.java

class JavaClass {
    void test(KotlinClass param) {
        param.nonExistingMethod("hello");
        param.nonExistingMethod(materialize());
    }

    <T> T materialize() {
        return null;
    }
}

// FILE: KotlinClass.kt
class KotlinClass
