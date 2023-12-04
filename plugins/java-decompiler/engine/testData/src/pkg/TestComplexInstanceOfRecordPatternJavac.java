package pkg;

public class TestComplexInstanceOfRecordPatternJavac {
    public static void main(String[] args) {

    }

    public static void instanceOfTest1(Object o) {
        if (o instanceof R(R(Object s1))) {
            System.out.println(s1);
            System.out.println(s1);
        }
        System.out.println("1");
    }

    public static void instanceOfTest2(Object o) {
        if (o instanceof R(R(String s1))) {
            System.out.println(s1);
            System.out.println(s1);
        }
        System.out.println("4");
    }

    public static void instanceOfTest3(Object o) {
        if (o instanceof R2(String s1, Object s2)) {
            System.out.println(s1);
            System.out.println(s1);
        }
        System.out.println("12");
    }

    public static void instanceOfTest3_2(Object o) {
        if (o instanceof R2(Object s2, String s1)) {
            System.out.println(s1);
            System.out.println(s1);
        }
        System.out.println("3");
    }

    public static void instanceOfTest4(Object o) {
        if (o instanceof R2(String s1, R(String s))) {
            System.out.println(s1);
            System.out.println(s);

            if (o instanceof R2(String s2, R(String s3))) {
                System.out.println(s2);
                System.out.println(s3);
            }
        }
        System.out.println("1");
    }

    record R(Object o) {
    }

    record R2(Object o1, Object o2) {
    }
}
