public class JavaClass<T> {
    public JavaClass() {
    }

    T getT() {
        throw new UnsupportedOperationException();
    }

    void takeCommonClassAsArg(T arg) {
    }

    static JavaClass<MppCommon> commonInstance = new JavaClass<MppCommon>();
    static JavaClass<MppExpectActual> expectActualInstance = new JavaClass<MppExpectActual>();

    static void testJvmOnlyPropertyIsAccessible() {
        new JavaClass<MppExpectActual>().getT().getJvmProp();
    }
}