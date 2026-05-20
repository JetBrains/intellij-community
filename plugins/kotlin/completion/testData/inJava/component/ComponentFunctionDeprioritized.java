package a;

public class Testing {
    public static void test() {
        Person person = new Person("Alice");
        person.<caret>
    }
}

// WITH_ORDER
// EXIST: getName
// EXIST: component1
