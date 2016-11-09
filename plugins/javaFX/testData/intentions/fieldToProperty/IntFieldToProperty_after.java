import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;

class IntDemo {
    SimpleIntegerProperty n = new SimpleIntegerProperty(this, "n");

    IntDemo(int n) {
        this.n.set(n);
    }

    public int getN() {
        return n.get();
    }

    public void setN(int n) {
        this.n.set(n);
    }

    public void preInc() {
        n.set(n.get() + 1);
    }

    public void postDec() {
        n.set(n.get() - 1);
    }

    public int preIncVal() {
        return ++n;
    }

    public void twice() {
        n.set((int) (n.get() * (2)));
    }

    public void half() {
        n.set((int) (n.get() >> (1)));
    }

    public int plusOne() {
        return n.get() + 1;
    }

    public void forLoop(int a) {
        for (n.set(0); n.get() < a; n.set(n.get() + 1)) {
            System.out.println(n.get());
        }
    }

    public I lambda() {
        return () -> n.get();
    }

    public String toString() {
        return "n=" + n.get();
    }

    interface I {
        int get();
    }
}