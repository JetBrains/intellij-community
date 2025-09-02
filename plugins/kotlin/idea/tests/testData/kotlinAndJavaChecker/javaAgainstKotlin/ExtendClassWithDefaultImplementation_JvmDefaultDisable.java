package test;

public class ExtendClassWithDefaultImplementation_JvmDefaultDisable {
    public static class ExtendClass extends KotlinClass {

    }

    <error descr="Class 'ImplementInterface' must either be declared abstract or implement abstract method 'bar()' in 'KotlinInterface'">public static class ImplementInterface implements KotlinInterface</error> {
        @Override
        public void f() {

        }
    }
}
