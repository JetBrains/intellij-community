import testData.libraries.*;

class TestOverload {

    void foo() {
        String s = testData.libraries.GlobalPropertyKt.getGlobalVal();
        Long l = GlobalPropertyKt.getGlobalValWithGetter();
    }

}