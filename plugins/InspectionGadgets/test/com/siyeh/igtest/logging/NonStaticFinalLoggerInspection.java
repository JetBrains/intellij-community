package com.siyeh.igtest.logging;

import java.util.logging.Logger;

public class NonStaticFinalLoggerInspection {
    private Logger foo = Logger.getLogger("foo");
    private NonStaticFinalLoggerInspection() {
    }

    public static void main(String[] args) {

    }
}
