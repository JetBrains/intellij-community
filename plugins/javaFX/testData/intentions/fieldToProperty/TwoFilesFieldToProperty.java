import javafx.scene.Node;

class DoubleDemo {
    double <caret>data = 1.234;

    DoubleDemo(double d) {
        this.data = d;
    }

    public String toString() {
        return "data=" + data;
    }
}