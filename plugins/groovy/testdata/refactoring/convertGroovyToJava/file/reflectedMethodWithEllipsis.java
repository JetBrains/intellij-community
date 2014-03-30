public class MyClass {
public void foo(java.util.Map args, java.lang.String a, java.lang.String... b) {}

public void foo(java.lang.String a, java.lang.String... b) {
foo(new java.util.LinkedHashMap(), a, b);
}

}
