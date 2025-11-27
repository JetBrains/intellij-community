import testData.libraries.*;

class TestOverload {
    void test() {
        boolean prop = JvmStaticsPropertiesConflictingWithClass.getProp();
        JvmStaticsPropertiesConflictingWithClass.setProp(true);
        prop = JvmStaticsPropertiesConflictingWithClass.prop;
    }
}