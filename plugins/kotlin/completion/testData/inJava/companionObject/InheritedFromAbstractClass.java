package a;

public class Testing {
    public static void test() {
        Test.<caret>
    }
}

// EXIST: Companion.inheritedConcrete
// EXIST: Companion.abstractMember
