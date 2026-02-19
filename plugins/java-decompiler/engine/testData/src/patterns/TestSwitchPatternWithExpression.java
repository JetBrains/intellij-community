package decompiler;

public class TestSwitchPatternWithExpression {
    sealed interface I {
    }

    record A(String a) implements I {
    }

    record B(String a) implements I {
    }

    record AA(I i) {
    }

    public static void main(String[] args) {
        I i = new A("4265111111");
    }

    private static String getX(I i) {
        return switch (i) {
            case A(var a) -> a;
            case B(var a) -> a;
        };
    }

    private static String getX8(I i) {
        switch (i) {
            case A(var a) -> {
                return a;
            }
            case B(var a) -> {
                return a;
            }
        }
    }

    private static String getX0(AA i) {
        return switch (i) {
            case AA(A(var a)) -> a;
            case AA(B(var a)) -> a;
        };
    }

    private static void getX11(AA i) {
        String aa = switch (i) {
            case AA(A(var a)) -> a;
            case AA(B(var a)) -> a;
        };
        System.out.println(aa + "1");
    }

    private static String getX4(I i) {
        String string = switch (i) {
            case A(var a) -> a;
            case B(var a) -> a;
        };
        return string;
    }

    private static void getX10(I i) {
        String string = switch (i) {
            case A(var a) -> a;
            case B(var a) -> a;
        };
        System.out.println(string + "2");
    }

    private static String getX5(I i) {
        String string = switch (i) {
            case A(var a) -> a + "1";
            case B(var a) -> a;
        };
        return string;
    }

    private static void getX9(I i) {
        String string = switch (i) {
            case A(var a) -> a + "1";
            case B(var a) -> a;
        };
        System.out.println(string + "2");
    }

    private static String getX3(I i) {
        return switch (i) {
            case A(var a) -> {
                System.out.println(a);
                yield a;
            }
            case B(var a) -> {
                System.out.println(a);
                yield a;
            }
        };

    }

    private static String getX6(I i) {
        return switch (i) {
            case A(var a) -> {
                System.out.println(a);
                yield a;
            }
            case B(var a) -> {
                System.out.println(a);
                yield a + "1";
            }
        };
    }

    private static String getX7(I i) {
        return switch (i) {
            case A(var a) -> {
                System.out.println(a);
                System.out.println(a);
                System.out.println(a + "1");
                yield a;
            }
            case B(var a) -> {
                System.out.println(a);
                yield a + "1";
            }
        };
    }

    private static String getX2(I i) {
        switch (i) {
            case A(var a):
                return a;
            case B(var a):
                return a;
            default:
                throw new IllegalArgumentException();
        }
    }
}