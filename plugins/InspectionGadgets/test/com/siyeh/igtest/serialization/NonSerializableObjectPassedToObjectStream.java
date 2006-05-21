package com.siyeh.igtest.serialization;

import com.siyeh.ig.fixes.RenameFix;

import java.io.ObjectOutputStream;
import java.io.IOException;

public class NonSerializableObjectPassedToObjectStream {
    public void foo() throws IOException {
        final ObjectOutputStream stream = new ObjectOutputStream(null);
        stream.writeObject(new Integer(3));
        stream.writeObject(new RenameFix());
    }
}
