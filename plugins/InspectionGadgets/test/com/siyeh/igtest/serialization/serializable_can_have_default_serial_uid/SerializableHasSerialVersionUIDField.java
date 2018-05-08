package com.siyeh.igtest.serialization.serializable_can_have_default_serial_uid;

import java.io.Serializable;

public class Serial implements Serializable {
    private static final long serialVersionUID = 123L;

    void foo() {}

    int m = 2;
}