import javafx.beans.property.SimpleLongProperty;

class LongDemo {
    private SimpleLongProperty n = new SimpleLongProperty(this, "n");

    public long getN() {
        return n.get();
    }

    public void setN(long n) {
        this.n.set(n);
    }

    public void preInc() {
        n.set(n.get() + 1);
    }

    public long preIncVal() {
        return ++n;
    }

    public void postDec() {
        n.set(n.get() - 1);
    }

    public void twice() {
        n.set((long) (n.get() * (2)));
    }

    public void half() {
        n.set((long) (n.get() >> (1)));
    }

    public long plusOne() {
        return n.get() + 1;
    }

    public L lambda() {
        return () -> n.get();
    }

    public String toString() {
        return "n=" + n.get();
    }

    interface L {
        long get();
    }
}