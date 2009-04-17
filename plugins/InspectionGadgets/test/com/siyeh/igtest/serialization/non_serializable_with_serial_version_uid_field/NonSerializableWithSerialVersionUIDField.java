package com.siyeh.igtest.serialization.non_serializable_with_serial_version_uid_field;

import java.io.Serializable;

public class NonSerializableWithSerialVersionUIDField {

    private static final long serialVersionUID = -25111423935153899L;

    void foo() {
        new Object() {
            private static final long serialVersionUID = -25111423935153899L;
        };
    }
}
@interface X {
    long serialVersionUID = -25111423935153899L;
}
interface Y {
    long serialVersionUID = -25111423935153899L;
}
