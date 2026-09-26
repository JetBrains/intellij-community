import kotlin.Pair;

public class Test {
    public void test(Pair<String, String> pair) {
        String first = pair.<caret>component1();
    }
}
