
interface I {
    static <Z> void FOO() {

    }
}

class A implements I  {
    {
        A.<String>F<caret>OO();
    }
}

