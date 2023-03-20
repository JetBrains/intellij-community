package one.two;

import one.two.KotlinClass.Named.NestedObject;

public class Usage {
    void t() {
        NestedObject.INSTANCE.extension(new Receiver(), 4);
    }
}