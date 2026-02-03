package pkg;


public class TestLVTScoping {
    public static void method() {
        String a;
        if (1 == Integer.valueOf(1)) {
            a = "YAY";
        } else {
            a = "NAY";
        }
        System.out.println(a);
    }
    public static void method2() {
        String a;
        if (1 == Integer.valueOf(1)) {
            a = "YAY";
        } else {
            a = "NAY";
            System.out.println(a);
        }
    }
    public static void method3() {
        if (1 == Integer.valueOf(1)) {
            boolean a;
            a = true;
            System.out.println(a);
        } else {
            String a;
            a = "NAY";
            System.out.println(a);
        }
    }
}
