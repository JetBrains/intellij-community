// FILE: main.kt
import dependency.JavaClass

class Foo {
    operator fun invoke(): String = "foo"
}

fun usage(fromJava: JavaClass<Foo>) {
    // K2 supports such code, K1 doesn't
    fromJava.val<caret>ue()
}

// K1_TYPE: value -> <html>Type is unknown</html>

// K2_TYPE: value -> <b>Foo!</b>

// FILE: dependency/JavaClass.java
package dependency;

public class JavaClass<T> {
    public T getValue() { return null; }
}