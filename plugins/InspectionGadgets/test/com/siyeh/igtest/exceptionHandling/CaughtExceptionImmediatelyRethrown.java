package com.siyeh.igtest.exceptionHandling;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class CaughtExceptionImmediatelyRethrown {

    void foo() throws FileNotFoundException {
        try {
            new FileInputStream(new File(""));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            throw e;
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    void conflict() throws FileNotFoundException {
        try {
            int i = 0;
            new FileInputStream(new File(""));
        } catch (FileNotFoundException e) {
            throw e;
        }
        int i = 10;
    }

}