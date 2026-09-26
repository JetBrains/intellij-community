package a;

public class Testing {
    public static void test() {
        Point point = new Point(1, 2, 3);
        point.<caret>
    }
}

// WITH_ORDER
// EXIST: getX
// EXIST: getY
// EXIST: getZ
// EXIST: component1
// EXIST: component2
// EXIST: component3
