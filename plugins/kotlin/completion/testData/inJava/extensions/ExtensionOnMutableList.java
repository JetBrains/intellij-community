package a;

import java.util.ArrayList;

public class Testing {
    public static void test() {
        ArrayList<String> list = new ArrayList<>();
        list.<caret>
    }
}
// EXIST: addIfAbsent
