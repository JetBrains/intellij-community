// "Replace with 'List.subList().clear()'" "GENERIC_ERROR_OR_WARNING"
import java.util.List;

class X {
    void test(List<String> list, int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException();
        }
        f<caret>or (int i = to; i >= from; i--) {
            list.remove(i);
        }
    }
}
