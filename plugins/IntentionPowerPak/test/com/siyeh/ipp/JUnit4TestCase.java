package com.siyeh.ipp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class JUnit4TestCase {
    @Test
    public void foo() {
        String x = "bar" + "baz";
        assertEquals(x, "foo");
        assertEquals(3, 4);
        assertEquals(3, 4);
        assertTrue(true);
    }
}
