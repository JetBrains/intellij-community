import java.util.Iterator;
import java.util.List;

class MyTest {
    void test(List<? extends String> list) {
        fo<caret>r (String aList: list) {
            System.out.println(aList);
        }
        Iterator<? extends String> it = null;
    }
}
