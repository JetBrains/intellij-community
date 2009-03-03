package com.siyeh.igtest.junit;

import junit.framework.TestCase;

import java.util.Collection;

public class SimplifiableJUnitAssertionInspection extends TestCase{
    public void test()
    {
        assertTrue(3 == 4);
        assertEquals(false, new Object() != null);
        assertTrue(false);
        assertFalse("foo", true);
        Collection collection = null;
        assertTrue(collection.size() == 2);
    }


    static class IDEABugTest extends TestCase {
        public static enum Input { value1 }

        public void testAssertEqualsSimplificationShouldNotSimplifyOverridenAssertEquals() throws Exception {
            assertEquals(Input.value1, null, new Exception());
            assertEquals("value1", null, new Exception());
            assertEquals("value1", true, new Exception());
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public static void assertEquals(Enum expectedValue1, Integer expectedValue2, Exception actual) {
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public static void assertEquals(String expectedValue1, Integer expectedValue2, Exception actual) {
        }

    }
}
