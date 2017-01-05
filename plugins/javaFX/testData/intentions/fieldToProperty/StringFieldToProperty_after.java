import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;

class StringDemo {
    SimpleStringProperty s = new SimpleStringProperty(this, "s", "Something");

    StringDemo(String s) {
        this.s.set(s);
    }

    public void compound(String t) {
        s.set(s.get() + (t));
    }

    public String toString() {
        return "s=" + s.get();
    }
}