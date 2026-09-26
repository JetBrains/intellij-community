import testData.libraries.*;

class TestOverload {
    void test() {
        String def = CapitalizedPropertyContainerObject.getDefault();
        CapitalizedPropertyContainerObject.setDefault("def");
    }
}