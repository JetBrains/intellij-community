import java.util.*;

class Bar {
    public int xxx(Bar p) { return 1; }
}

class Test {
    Comparator<Bar> comparator = Bar:<caret>:xxx;
}