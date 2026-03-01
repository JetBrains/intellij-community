public class TestTernaryBoxingStatement {

    public static void main(String[] args) {

    }

    public void foo(Object o) {
        Boolean b0 = false;
        byte a1 = 4;
        final byte a2 = 34;
        Byte a = ((b0) ? a1 : a2);
    }
}