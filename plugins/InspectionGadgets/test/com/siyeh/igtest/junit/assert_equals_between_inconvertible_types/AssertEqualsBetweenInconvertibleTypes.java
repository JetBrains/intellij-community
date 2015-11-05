package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.util.*;

public class AssertEqualsBetweenInconvertibleTypes {

    @Test
    public void test() {
        Double d = 1.0;
        assertEquals(1.0, d , 0.0); // fine.
        assertEquals(1.0, d , 0); //  'assertEquals()' between inconvertible types 'Double' and 'int'
        assertEquals(1, d , 0.0); //  Doesn't complain even though perhaps it should.
    }

    public void testFoo() {
        org.junit.Assert.<warning descr="'assertEquals()' between objects of inconvertible types 'String' and 'double'">assertEquals</warning>("java", 1.0);
    }

    @Test
    public void testCollection() {
        Collection<A> c1 = null;
        Collection<B> c2 = null;
        assertEquals(c1, c2);
        assertEquals(new ArrayList<String>(){}, new ArrayList<String>());
    }

    interface A {}
    interface B extends A {}

    private static class GenericClass<T> {}

    public static boolean areEqual(Object a, GenericClass<String> b) {
        return a.equals(b);
    }
}