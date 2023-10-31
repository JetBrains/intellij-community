package pkg;

public class TestSwitchClassReferencesEcj{

    public static void testObject(Object o) {
        switch (o) {
            case String s -> System.out.println("s");
            case Integer i -> System.out.println("i");
            case Object ob -> System.out.println(o);
        }
    }

    public static void testObject2(Object o) {
        switch (o) {
            case String s -> System.out.println("s");
            case Integer i -> System.out.println("i");
            case Object ob -> System.out.println(o);
            case null -> System.out.println("null");
        }
    }

    public static void testObject3(Object o) {
        switch (o) {
            case String s -> System.out.println("s");
            case Integer i -> System.out.println("i");
            case null -> System.out.println("null");
            default -> System.out.println("o");
        }
    }

    public static void testObject4(Object o) {
        switch (o) {
            case String s -> System.out.println("s");
            case Integer i -> System.out.println("i");
            default -> System.out.println("o");
        }
    }
}
