public class Test {
    boolean A, B, C;
    boolean f() {
        return A &<caret>& B || C;
    }
}
