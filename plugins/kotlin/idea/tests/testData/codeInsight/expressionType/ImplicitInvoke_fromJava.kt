// FILE: main.kt
import dependency.JavaClass

fun usage(fromJava: JavaClass) {
    from<caret>Java()
}

// K1_TYPE: fromJava -> <html>JavaClass</html>
// K1_TYPE: fromJava() -> <html>String!</html>

// K2_TYPE: fromJava -> <b>JavaClass</b>
// K2_TYPE: fromJava() -> <b>String!</b>

// FILE: dependency/JavaClass.java
package dependency;

public class JavaClass {
    public String invoke() { return "foo"; }
}