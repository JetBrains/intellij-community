package one.two;

import one.two.KotlinClass.Companion.NestedObject;

public class Usage {
    void t() {
        NestedObject.staticExtension(new Receiver(), 4);
    }
}