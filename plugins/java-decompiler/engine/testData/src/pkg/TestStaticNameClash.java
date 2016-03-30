package pkg;

/**
 * @author Alexandru-Constantin Bledea
 * @since March 07, 2016
 */
public class TestStaticNameClash {

    public static String property;

    public static void setProperty(final String property) {
        TestStaticNameClash.property = property;
    }

}
