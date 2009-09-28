package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class AssertEqualsBetweenInconvertibleTypes {

    @Test
    public void test() {
        Double d = 1.0;
        assertEquals(1.0, d , 0.0); // fine.
        assertEquals(1.0, d , 0); //  'assertEquals()' between inconvertable types 'Double' and 'int'
        assertEquals(1, d , 0.0); //  Doesn't complain even though perhaps it should.
    }

    public void testFoo() {
        org.junit.Assert.assertEquals("java", 1.0);
    }
}