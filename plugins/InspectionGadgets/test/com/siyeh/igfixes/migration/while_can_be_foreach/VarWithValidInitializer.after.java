import java.util.List;

class MyTest {
    void test(List<? extends String> list) {
        fo<caret>r (String s : list) {
            System.out.println(s);
        }
        var it = list.iterator();
    }
}
