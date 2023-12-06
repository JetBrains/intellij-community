package usages;
import library.*;

class J {
    void foo() {
        LibraryKt.fooWithOverloads();
        LibraryKt.fooWithOverloads(1);
        LibraryKt.fooWithOverloads(1, 1.0);
        LibraryKt.fooWithOverloads(1, 1.0, "1");
    }
}