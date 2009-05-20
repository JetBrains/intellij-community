public class Test {
    int method(int i) {
        return 0;
    }

    int m(int i, int j, Test t) {
        return i + <selection>t.method(j)</selection>;
    }
}