package com.siyeh.igtest.junit;

import junit.framework.TestCase;

import java.util.Collection;
import java.util.Objects;

public class SimplifiableJUnitAssertion extends TestCase{
    public void test()
    {
        <warning descr="'assertTrue()' can be simplified to 'assertEquals()'">assertTrue</warning>(3 == 4);
        <warning descr="'assertEquals()' can be simplified to 'assertFalse()'">assertEquals</warning>(false, new Object() != null);
        <warning descr="'assertTrue()' can be simplified to 'fail()'">assertTrue</warning>(false);
        <warning descr="'assertFalse()' can be simplified to 'fail()'">assertFalse</warning>("foo", true);
        Collection collection = null;
        <warning descr="'assertTrue()' can be simplified to 'assertEquals()'">assertTrue</warning>(collection.size() == 2);
    }

    public void testObjectEquals() {
        <warning descr="'assertTrue()' can be simplified to 'assertEquals()'">assertTrue</warning>(Objects.equals("foo", "bar"));
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
