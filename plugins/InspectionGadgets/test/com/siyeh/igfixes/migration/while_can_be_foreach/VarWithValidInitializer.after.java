import java.util.List;

class MyTest {
    void test(List<? extends String> list) {
        fo<caret>r (String aList : list) {
            System.out.println(aList);
        }
        var it = list.iterator();
    }
}
