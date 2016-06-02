package pkg;

public class TestStaticNameClash {

    public static String property;

    public static void setProperty(final String property) {
        TestStaticNameClash.property = property;
    }

}
