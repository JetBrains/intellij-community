import javafx.scene.Node;

class IntDemo {
    int <caret>n;

    IntDemo(int n) {
        this.n = n;
    }

    public int getN() {
        return n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public void preInc() {
        ++n;
    }

    public void postDec() {
        n--;
    }

    public int preIncVal() {
        return ++n;
    }

    public void twice() {
        n *= 2;
    }

    public void half() {
        n >>= 1;
    }

    public int plusOne() {
        return n + 1;
    }

    public void forLoop(int a) {
        for (n = 0; n < a; n++) {
            System.out.println(n);
        }
    }

    public I lambda() {
        return () -> n;
    }

    public String toString() {
        return "n=" + n;
    }

    interface I {
        int get();
    }
}