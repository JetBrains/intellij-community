package com.siyeh.igtest.bugs;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.ArrayList;

public class IteratorTest implements Iterator{
    public void remove() {
    }

    public boolean hasNext() {
        return false;
    }

    public Object next() {
        throw new NoSuchElementException();
    }
}
