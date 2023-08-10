import testData.libraries.*;

class TestOverload {
    void foo() {
        OverloadedFunKt.overloadedFun("", null, 2, null);

        OverloadedFunKt.overloadedFun("", null, true, 2, null);

        OverloadedFunKt.overloadedFun("", null, true, 2, 3, null);
    }

    void test() {
        OverloadedFunKt.funWithDefaultParameter(42);
        OverloadedFunKt.funWithDefaultParameter(42, "awd");
        var o = new OpenClassWithFunctionWithDefaultParameter();
        o.doSmth();
        o.doSmth(false);

        var c = new ChildOfOpenClassWithFunctionWithDefaultParameter();
        c.doSmth();
        c.doSmth(false);
    }
}