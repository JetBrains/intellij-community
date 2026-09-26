// FILE: main.kt
import dependency.JavaClass

fun usage(fromJava: JavaClass<() -> String>) {
    // K2 supports such code, K1 doesn't
    fromJava.val<caret>ue()
}

// K1_TYPE: value -> <html>Type is unknown</html>

// K2_TYPE: value -> <b>() -&gt; String!</b>

// FILE: dependency/JavaClass.java
package dependency;

public class JavaClass<T> {
    public T getValue() { return null; }
}