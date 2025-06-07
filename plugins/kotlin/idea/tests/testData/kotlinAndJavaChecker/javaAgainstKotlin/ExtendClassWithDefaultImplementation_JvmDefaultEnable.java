package test;

public class ExtendClassWithDefaultImplementation_JvmDefaultEnable {
    public static class ExtendClass extends KotlinClass {

    }

    public static class ImplementInterface implements KotlinInterface {
        @Override
        public void f() {

        }
    }
}
