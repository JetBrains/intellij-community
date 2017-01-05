import javafx.scene.Node;

class StringDemo {
    String <caret>s = "Something";

    StringDemo(String s) {
        this.s = s;
    }

    public void compound(String t) {
        s += t;
    }

    public String toString() {
        return "s=" + s;
    }
}