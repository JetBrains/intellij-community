package one.two;

import one.two.KotlinClass.Named.NestedObject;

public class Usage {
    void t() {
        NestedObject.staticExtension(new Receiver(), 4);
    }
}