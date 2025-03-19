package pkg;

public class TestSwitchWithDeconstructionsWithoutNestedJavac {
    public static void main(String[] args) {

    }

  record R1(Object o, Object o2) {

  }

  public static void testStringString(Object o) {
        switch (o) {
            case R1(String s1, String s2) -> {
                if (s2.isEmpty()) {
                    System.out.println("2");
                }
            }
            default -> System.out.println("3");
        }
        System.out.println("1");
    }

    public static void testStringObjectWhen(Object o) {
        switch (o) {
            case R1(String s1, Object s2) when s1.hashCode() == 3 -> {
                if (s1.hashCode() == 1) {
                    System.out.println("2");
                    System.out.println("2");
                    System.out.println("2");
                }
            }
            default -> System.out.println("3");
        }
        System.out.println("1");
    }

    public static void testStringObject(Object o) {
        switch (o) {
            case R1(String s1, Object s2) -> {
                if (s1.isEmpty()) {
                    System.out.println("1");
                }
            }
            default -> System.out.println("3");
        }
        System.out.println("1");
    }

    public static void testObjectString(Object o) {
        switch (o) {
            case R1(Object s1, String s2) -> {
                if (s1.hashCode() == 1) {
                    System.out.println("1");
                }
            }
            default -> System.out.println("3");
        }
        System.out.println("1");
    }

    public static void testObjectObject(Object o) {
        switch (o) {
            case R1(Object s1, Object s2) -> {
                if (s1.hashCode() == 1) {
                    System.out.println("1");
                }
            }
            default -> System.out.println("3");
        }
        System.out.println("1");
    }
}
