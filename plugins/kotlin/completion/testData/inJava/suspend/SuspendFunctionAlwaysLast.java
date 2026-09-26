package a;

public class Testing {
    public static void test() {
        Service service = new Service();
        service.<caret>
    }
}

// WITH_ORDER
// EXIST: betaNormal
// EXIST: deltaNormal
// EXIST: epsilonNormal
// EXIST: alphaSuspend
// EXIST: gammaSuspend
