import java.util.Comparator;
class MyTest {
    void setComparator(Comparator<?> comparator) {}
    String getValue() {
        return "";
    }

    {
        setComparator(Comparator.comparing(MyTest::<caret>getValue));
    }
}