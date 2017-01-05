import javafx.scene.Node;

class DoubleDemo2 {
    static void foo(DoubleDemo dd) {
        double d = dd.data.get();
        dd.data.set(d + 2);
    }
}