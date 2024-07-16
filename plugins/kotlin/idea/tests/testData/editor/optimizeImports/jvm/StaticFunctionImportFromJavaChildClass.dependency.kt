class Base {
    public static void staticFunFromBase() {}
    public static String STATIC_CONSTANT_FROM_BASE = "";

    public String nonStaticFieldFromBase = "";
}

class Child extends Base {}