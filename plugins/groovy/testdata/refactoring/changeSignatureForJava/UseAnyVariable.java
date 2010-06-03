import java.util.List;

class C {
    void <caret>method() {
    }
}

class Usage {
    List myList;
    {
        C c = new C();
        c.method();
    }
}