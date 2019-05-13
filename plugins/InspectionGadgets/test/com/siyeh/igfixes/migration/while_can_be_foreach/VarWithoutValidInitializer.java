import java.util.List;

class MyTest {
    void test(List<? extends String> list) {
        var it = list.iterator();
        wh<caret>ile(it.hasNext()) {
            System.out.println(it.next());
        }
        it = null;
    }
}
