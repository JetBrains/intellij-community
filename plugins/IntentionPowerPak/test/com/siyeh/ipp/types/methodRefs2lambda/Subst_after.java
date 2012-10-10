import java.util.*;

class Bar {
    public int xxx(Bar p) { return 1; }
}

class Test {
    Comparator<Bar> comparator = (o1, o2) -> o1.xxx(o2);
}