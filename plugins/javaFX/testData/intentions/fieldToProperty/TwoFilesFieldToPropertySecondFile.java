import javafx.scene.Node;

class DoubleDemo2 {
    static void foo(DoubleDemo dd) {
        double d = dd.data;
        dd.data = d + 2;
    }
}