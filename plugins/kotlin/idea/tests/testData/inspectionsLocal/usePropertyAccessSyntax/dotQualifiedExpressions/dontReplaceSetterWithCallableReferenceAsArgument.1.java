import java.util.concurrent.Callable;

public class J {

    private int x;

    public int setX(Callable<Integer> callable) {
        this.x = x;
        return x;
    }

    public int getX() {
        return x;
    }
}
