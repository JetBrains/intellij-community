package a;

public class Testing {
    public static void test() {
        Service service = new Service();
        service.<caret>
    }
}

// WITH_ORDER
// EXIST: firstNormal
// EXIST: secondNormal
// EXIST: firstSuspend
// EXIST: secondSuspend
