package com.siyeh.ipp.exceptions.splitTry;

import java.io.*;

public class WithCatch {
    void foo(File file1, File file2) {
        try (FileInputStream in = new FileInputStream(file1); <caret>FileOutputStream out = new FileOutputStream(file2)) {

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}