import testData.libraries.*;

class TestOverload {
    {
        ExplicitOverloadsKt.func(5);
        ExplicitOverloadsKt.func(5, "5");
        ExplicitOverloadsKt.func(5, 5);
        ExplicitOverloadsKt.func();
    }
}