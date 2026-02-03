import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;

class DoubleDemo {
    SimpleDoubleProperty data = new SimpleDoubleProperty(this, "data", 1.234);

    DoubleDemo(double d) {
        this.data.set(d);
    }

    public String toString() {
        return "data=" + data.get();
    }
}