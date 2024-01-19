// "Add method 'qq' to 'CollectionsKt'" "false"
// ACTION: Rename reference
// WITH_STDLIB
import kotlin.collections.CollectionsKt;

class J {
    void test() {
        CollectionsKt.qq<caret>();
    }
}