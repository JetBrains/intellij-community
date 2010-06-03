import java.util.List;

class C {
    void method(List l) {
    }
}

class Usage {
    List myList;
    {
        C c = new C();
        c.method(myList);
    }
}