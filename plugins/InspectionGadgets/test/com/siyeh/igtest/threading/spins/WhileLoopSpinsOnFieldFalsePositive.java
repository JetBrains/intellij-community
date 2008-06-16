package com.siyeh.igtest.threading.spins;

import java.io.InputStream;
import java.io.IOException;

class WhileLoopSpinsOnFieldFalsePositive {

    private InputStream source;

    private long remaining;

    WhileLoopSpinsOnFieldFalsePositive(InputStream source) {
        if (source == null) {
            throw new NullPointerException("source is null");
        }
        this.source = source;
        remaining = 0L;
    }

    public long nextElement() throws IOException {
        while (remaining > 0) {
            remaining -= source.skip(remaining);
        }
        return -1L;
    }
}