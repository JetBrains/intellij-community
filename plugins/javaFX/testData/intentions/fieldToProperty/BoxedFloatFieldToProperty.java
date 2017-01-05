import javafx.scene.Node;

class BoxedFloatDemo {
    private Float <caret>f;

    BoxedFloatDemo(Float f) {
        this.f = f;
    }

    public Float getF() {
        return f;
    }

    public void setF(Float f) {
        this.f = f;
    }

    public void preInc() {
        ++f;
    }

    public void postDec() {
        f--;
    }

    public void twice() {
        f *= 2;
    }

    public void half() {
        f /= 1;
    }

    public Float plusOne() {
        return f + 1;
    }

    public F lambda() {
        return () -> f;
    }

    public String toString() {
        return "f=" + f;
    }

    interface F {
        Float get();
    }
}