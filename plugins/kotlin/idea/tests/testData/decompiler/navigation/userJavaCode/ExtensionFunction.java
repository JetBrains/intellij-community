import testData.libraries.*;

class TestOverload {
    {
        Integer v = ExtensionFunctionKt.filter(5, x -> x % 2 == 1);
    }
}