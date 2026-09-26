public class NestedObjectInstanceUsage {
    void t() {
        MyObject.Nested instance = MyObject.Nested.INST<caret>ANCE;
    }
}