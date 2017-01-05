import javafx.beans.property.SimpleFloatProperty;
import javafx.scene.Node;

class BoxedFloatDemo {
    private SimpleFloatProperty f = new SimpleFloatProperty(this, "f");

    BoxedFloatDemo(Float f) {
        this.f.set(f);
    }

    public Float getF() {
        return f.get();
    }

    public void setF(Float f) {
        this.f.set(f);
    }

    public void preInc() {
        f.set(f.get() + 1);
    }

    public void postDec() {
        f.set(f.get() - 1);
    }

    public void twice() {
        f.set((float) (f.get() * (2)));
    }

    public void half() {
        f.set((float) (f.get() / (1)));
    }

    public Float plusOne() {
        return f.get() + 1;
    }

    public F lambda() {
        return () -> f.get();
    }

    public String toString() {
        return "f=" + f.get();
    }

    interface F {
        Float get();
    }
}