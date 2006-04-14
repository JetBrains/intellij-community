package com.siyeh.ipp;

import org.junit.Assert;

import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

// Note: this class never gets compiled.  It's simply here to hold a
//bunch of manual test cases

public class ManualTestClass
{
    int foo[];
    int[] bar[];

    public void testInteger()
    {
        // convert between octal, hex, and decimal
        int foo = 31;

        // convert between octal, hex, and decimal
        long bar = 0x1fL;

// convert between octal, hex, and decimal
        int foo2 = 02556055060602406713736772653302544613000000000000000;

// convert between octal, hex, and decimal
        long bar2 = 31000000000000000000000000000000000000000000000L;

    }
    public void testFloat()
    {
        // convert between hex, and decimal
        float foo = 0x1.fp4f;
        double doubleFoo = 31.2;
    }

    public void testShift()
    {
        int x = 1;
        int y = 2;

        // test that this will go to shift and back
        x = y * 8;

        // test that this doesn't do anything
        x = y * 7;

        // test that this will go to shift and back
        x *= 8;

        // test that this doesn't do anything
        x *= 7;

        // test that this will go to shift and back
        x = y / 8;

        // test that this doesn't do anything
        x = y / 7;

        // test that this will go to shift and back
        x /= 8;

        // test that this doesn't do anything
        x /= 7;


        // test that this will go to shift and back
        x = 3 + y * 8;
    }

    public void testFQNames()
    {
        // test that this will be transformed
        java.util.HashMap hashMap = new java.util.HashMap();
    }

    public void testOpAssign()
    {
        int a = 0;
        int[] b = new int[1];

        // test that this will be shortened
        a = a + 3;

        // test that this will be shortened
        a = a * 3;

        // test that this will be shortened
        a = a - 3;

        // test that this will be shortened
        a = a % 3;

        // test that this will be shortened
        a = a / 3;

        // test that this will be shortened
        a = (a) / 3;

        // test that this will be shortened
        b[a] = b[a] / 3;

        // test that this will be shortened
        b[a] = b[(a)] / 3;

        // test that this does nothing
        b[a++] = b[a++] / 3;
    }

    public void testAnd()
    {
        boolean foo = true;
        boolean bar = true;
        boolean baz = true;

        // test that this will be changed
        if(foo && bar)
        {

        }
        // test that this will be changed
        if(foo && bar && baz)
        {

        }

        // test that this will be changed
        if(!foo && bar && !baz)
        {

        }

        // test both of these
        if((foo && bar) && baz)
        {

        }

        // test that this will be changed
        if(3 < 4 && 5 != 2)
        {

        }
    }

    public void testOr()
    {
        boolean foo = true;
        boolean bar = true;
        boolean baz = true;

        // test that this will be changed
        if(foo || bar)
        {

        }
        // test that this will be changed
        if(foo || bar || baz)
        {

        }

        // test that this will be changed
        if(!foo || bar || !baz)
        {

        }

        // test both of these
        if((foo || bar) || baz)
        {

        }
    }

    public void testBoolEquality()
    {
        boolean foo = true;

        // test that this collapses to if(foo)
        if(foo == true)
        {

        } // test that this collapses to if(!foo)
        if(foo == false)
        {

        } // test that this collapses to if(!foo)
        if(foo != true)
        {

        } // test that this collapses to if(foo)
        if(foo != false)
        {

        }   // test that this collapses to if(foo)
        if(true == foo)
        {

        }
        // test that this collapses to if(!foo)
        if(false == foo)
        {

        } // test that this collapses to if(!foo)
        if(true != foo)
        {

        } // test that this collapses to if(foo)
        if(false != foo)
        {
        }
        // test that this collapses to if(foo)
        if(true == 3 > 4)
        {
        }
        // test that this collapses to if(foo)
        if(true != 3 > 4)
        {
        }

    }

    public void testEquality()
    {
        String foo = "foo";
        String bar = "bar";
        int foo2 = 100;
        int baz2 = 3;

        // test turning this into .equals and back
        if(foo == bar)
        {

        }

        // test that this doesn't have a "change to equals"  intention
        if(foo2 == baz2)
        {

        }


        // test that this does have a "change to equals"  intention
        if("foo" + "bar" == "bar" + "foo")
        {

        }

        // test turning this into ! .equals and back
        if(foo != bar)
        {

        }

        // test that this doesn't have a "change to ! equals"  intention
        if(foo2 != baz2)
        {

        }

    }

    public void testFlipEquals()
    {
        String foo = "foo";
        String bar = "bar";

        // test flipping this
        if(foo.equals(bar))
        {

        }
        // test flipping this
        if("foo".equals("foo" + "bar"))
        {

        }
        // test flipping this
        if(foo.equalsIgnoreCase(bar))
        {

        }
    }

    public void testJUnit()
    {
        boolean foo = true;
        String bar = "foo";

        // test changing this to assertEquals  and back
        Assert.assertTrue("foo", foo);

        // test changing this to assertEquals  and back
        assertTrue(foo);

        // test changing this to assertEquals  and back
        assertFalse("foo", foo);

        // test changing this to assertEquals  and back
        assertEquals(false, foo);

        // test changing this to assertEquals  and back
        assertNull("foo", bar);

        // test changing this to assertEquals  and back
        assertNull(bar);

        // test changing this to assertTrue  and back
        assertEquals("foo", true, foo);

        // test changing this to assertTrue  and back
        assertEquals(true, foo);

        // test changing this to assertNull  and back
        assertEquals("foo", null, bar);

        // test changing this to assertNull  and back
        assertEquals(null, bar);


        // test changing this to assertFalse  and back
        assertTrue(foo);

        // test changing this to assertTrue  and back
        assertFalse("foo", foo);

        String baz1 = "foo";
        String baz2 = "bar";

        // test changing this to assertTrue and back
        assertEquals(baz1, baz2);

        // test changing this to assertTrue and back
        assertEquals("test", true, baz1.equals(baz2));

        int int1 = 3;
        int int2 = 4;
        // test changing this to assertTrue and back
        assertEquals(int1, int2);

        // test changing this to assertTrue and back
        assertEquals("test", int1, int2);

    }

    public void testMergeAndIf()
    {
        boolean foo = true;
        boolean bar = true;

        //test merge these
        if(foo)
        {
            if(bar)
            {
                System.out.println("test2");
            }
        }

        //test merge these
        if(foo)
            if(bar)
                System.out.println("test2");

        //test merge these
        if(foo)
        {
            if(bar)
                System.out.println("test2");
        }

        //test merge these
        if(foo)
            if(bar)
            {
                System.out.println("test2");
            }

        //test that these don't merge
        if(foo)
        {
            if(bar)
            {
                System.out.println("test2");
            }
            else
            {
                System.out.println("test3");
            }
        }

        //test that these don't merge
        if(foo)
        {
            if(bar)
            {
                System.out.println("test2");
            }
        }
        else
        {
            System.out.println("test3");
        }
    }

    public void testMergeOrIf()
    {
        boolean foo = true;
        boolean bar = true;
        if(foo)
        {
            System.out.println("1");
        }
        else if(bar)
        {
            System.out.println("1");
        }
        else
        {
            System.out.println("2");
        }

        // test that these don't merge
        if(foo)
        {
            System.out.println("1");
        }
        else if(bar)
        {
            System.out.println("3");
        }
        else
        {
            System.out.println("2");
        }
        // test that these don't merge
        if(foo)
        {
            System.out.println("1");
        }
        else if(bar)
        {
        }
        else
        {
            System.out.println("2");
        }

        if(foo)
        {
            System.out.println("2");
            return;
        }
         if(bar)
        {
            System.out.println("2");
             return;
        }


        if(foo)
        {
            System.out.println("1");
            return;
        }
         if(bar)
        {
            System.out.println("1");
             return;
        }
        else
        {
            System.out.println("2");
        }
    }

    public void testFlipConditional()
    {
        boolean foo = true;
        boolean bar = true;
        int x = 3;

        //test flipping this
        x = foo?3:4;
        System.out.println("x = " + x);
        x = foo != bar?3:4;
        System.out.println("x = " + x);
    }

    public void testSwitchToIf() throws InterruptedException
    {
        int x = 0;
        // test changing this to if-else
        switch(x)
        {
            case (3):
            case (4):
                System.out.println("3");
                System.out.println("4");
                break;
            case (5):
            case (6):
                System.out.println("5");
                System.out.println("6");
                break;
        }

        // test changing this to if-else
        switch(x)
        {
            case (3):
            case (4):
                System.out.println("3");
                System.out.println("4");
                break;
            case (5):
            case (6):
                System.out.println("5");
                System.out.println("6");
                break;
            default:
            case (7):
                System.out.println("default");
                break;
        }

        // test changing this to if-else
        switch(x)
        {
            case (3):
            case (4):
                //test comment
                System.out.println("3");
                //test comment3
                System.out.println("4");
                //test comment2
                break;
            default:
            case (7):
                System.out.println("default");
                break;
            case (5):
            case (6):
                System.out.println("5");
                System.out.println("6");
                break;
        }

        // test changing this to if-else  (fallthrough)
        switch(x)
        {
            case (3):
            case (4):
                System.out.println("3");
                System.out.println("4");
            case (5):
            case (6):
                System.out.println("5");
                System.out.println("6");
                break;
            default:
            case (7):
                System.out.println("default");
                break;
        }
        // test changing this to if-else  (fallthrough)
        switch(x)
        {
            case (3):
            case (4):
                System.out.println("3");
                System.out.println("4");
                break;
            case (5):
            case (6):
                System.out.println("5");
                System.out.println("6");
            default:
            case (7):
                System.out.println("default");
                break;
        }

        // test changing this to if-else  (fallthrough)
        switch(x)
        {
            case (3):
            case (4):
                System.out.println("3");
                System.out.println("4");
            case (5):
            case (6):
                System.out.println("5");
                System.out.println("6");
            default:
            case (7):
                System.out.println("default");
                break;
        }


        // test changing this to if-else (side effect on switch expression)
        int i;
        Label:
        switch(x++)
        {
            case (3):
            case (4):
                System.out.println("3");
                System.out.println("4");
                break;
            case (5):
            case (6):
                System.out.println("5");
                System.out.println("6");
                break;
            default:
            case (7):
                System.out.println("default");
                break;
        }

        // test changing this to if-else  (nested break)
        switch(x)
        {
            case (3):
            case (4):
                System.out.println("3");
                System.out.println("4");
                if(true)
                {
                    break;
                }
                break;
            case (5):
            case (6):
                System.out.println("5");
                System.out.println("6");
                break;
            default:
            case (7):
                System.out.println("default");
                break;
        }

        // test changing this to if-else  (declaration between branches)
        switch(x)
        {
            case (3):
            case (4):
                int y;
                Object foo;
                System.out.println("3");
                System.out.println("4");
                break;
            case (5):
            case (6):
                foo.wait(0);
                System.out.println("5");
                System.out.println("6");
                break;
            default:
            case (7):
                System.out.println("y = " + y);
                System.out.println("default");
                break;
        }

    }

    public void testFlipComparison()
    {
        int foo = 3;
        int bar = 4;

        //flip this
        if(3 > 4)
        {

        }
        //flip this
        if(3 >= 4)
        {

        }
        //flip this
        if(3 < 4)
        {

        }
        //flip this
        if(3 <= 4)
        {

        }
        //flip this
        if(3 == 4)
        {

        }
        //flip this
        if(3 != 4)
        {

        }
    }

    public void testNegateComparison()
    {
        int foo = 3;
        int bar = 4;

        //negate this
        if(3 > 4)
        {

        }
        //negate this
        if(3 >= 4)
        {

        }
        //negate this
        if(3 < 4)
        {

        }
        //negate this
        if(3 <= 4)
        {

        }
        //negate this
        if(3 == 4)
        {

        }
        //negate this
        if(3 != 4)
        {

        }
    }

    public void testRemoveConditional()
    {
        boolean foo = true;
        boolean baz = false;

        // test removing this conditional
        boolean bar = foo?true:false;

        // test removing this conditional
        bar = foo?false:true;

        //test removing this conditional
        bar = foo?(false):(true);

        // test that this conditional can't be removed
        bar = foo?true:true;

        // test that this conditional can't be removed
        bar = foo?false:false;
    }

    public void testRemoveIf()
    {
        boolean foo = false;
        boolean bar = true;

        // test removing this if
        if(!foo)
        {
            bar = true;
        }
        else
        {
            bar = false;
        }

        // test removing this if
        if(!foo)
        {
            bar = false;
        }
        else
        {
            bar = true;
        }

        // test that this if can't be removed
        if(foo)
        {
            bar = true;
        }
        else
        {
            bar = true;
        }

        // test that this if can't be removed
        if(!foo)
        {
            bar = false;
        }
        else
        {
            bar = false;
        }
    }

    public boolean testRemoveIfReturn1()
    {
        boolean foo = true;
        // test removing this
        if(!foo)
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public boolean testRemoveIfReturn2()
    {
        boolean foo = true;
        // test removing this
        if(!foo)
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    public boolean testRemoveIfReturn3()
    {
        boolean foo = true;
        // test that this if can't be removed
        if(!foo)
        {
            return true;
        }
        else
        {
            return true;
        }
    }

    public boolean testRemoveIfReturn()
    {
        boolean foo = true;
        // test that this if can't be removed
        if(!foo)
        {
            return false;
        }
        else
        {
            return false;
        }
    }

    public boolean testImplicitAssignIf()
    {
        boolean foo = true;
        boolean bar;
        // test that this if can't be removed
        bar = false;
        if(!foo)
        {
           bar = true;
        }
        return bar;
    }
    public boolean testImplicitAssignIfNegated()
    {
        boolean foo = true;
        boolean bar;
        // test that this if can't be removed
        bar = true;
        if(!foo)
        {
           bar = false;
        }
        return bar;
    }

    public boolean testRemoveIfImplicitReturn1()
    {
        boolean foo = true;
        // test removing this
        if(!foo)
        {
            return true;
        }
        return false;
    }

    public boolean testRemoveIfImplicitReturn2()
    {
        boolean foo = true;
        // test removing this
        if(!foo)
        {
            return false;
        }
        return true;
    }

    public boolean testRemoveIfImplicitReturn3()
    {
        boolean foo = true;
        // test that this if can't be removed
        if(!foo)
        {
            return true;
        }
        return true;
    }

    public boolean testRemoveIfImplicitReturn()
    {
        boolean foo = true;
        // test that this if can't be removed
        if(!foo)
        {
            return false;
        }
        return false;
    }

    public int testReplaceIfImplicitReturnWithConditional()
    {
        boolean foo = true;
        if(!foo)
        {
            return 3;
        }
        return 4;
    }

    public void testReplaceConditionalWithIfAssign()
    {
        boolean foo = true;
        int a;
        // test replacing this with if-then, and back again
        a = foo ? 3 : 4;
    }

    public void testReplaceConditionalWithIfDeclaration()
    {
        boolean foo = true;
        // test replacing this with if-then
        int a = foo?3:4;
    }

    public int testReplaceConditionalWithIfReturn()
    {
        boolean foo = true;
        // test replacing this with if-then, and back again
        return foo?3:4;
    }

    public void testIfToSwitch()
    {
        final int x = 2;
        Label:
        if(x == 4 || x == 3)
        {
            // test comment
            System.out.println("1");
        }
        else if(x == 5 || x == 6)
        {
            System.out.println("2");
        }
        else
        {
            System.out.println("3");
        }

        // test that the first and ssecond blocks remain wrapped
        if(x == 4 || x == 3)
        {
            int y = 3;
            System.out.println("1");
        }
        else if(x == 5 || x == 6)
        {
            int y = 4;
            System.out.println("2");
        }
        else
        {
            System.out.println("3");
        }

        // test that the first block remains wrapped, but the second doesn't
        if(x == 4 || x == 3)
        {
            int y = 3;
            System.out.println("1");
        }
        else if(x == 5 || x == 6)
        {
            for(int y = 0; y < 100; y++)
            {
                System.out.println("Barangus!");
            }
            System.out.println("2");
        }
        else
        {
            System.out.println("3");
        }

        for(; ;)
        {
            if(x == 4 || x == 3)
            {
                // test comment
                System.out.println("1");
                break;
            }
            else if(x == 5 || x == 6)
            {
                System.out.println("2");
            }
            else
            {
                System.out.println("3");
            }
        }
    }

    public void testIfToSwitchEnum()
    {
        final MyEnum x = MyEnum.Red;
        if((x == MyEnum.Red))
        {
            // test comment
            System.out.println("1");
        }
        else if((x == MyEnum.Blue) || x == MyEnum.Green)
        {
            System.out.println("2");
        }
        else
        {
            System.out.println("3");
        }
    }

    public void testFlipAnd()
    {
        boolean foo = true;
        boolean bar = false;
        boolean baz = false;

        // flip these
        if(foo && bar)
        {
            System.out.println("1");
        }
        // flip these
        if(foo && bar && baz)
        {
            System.out.println("1");
        }
    }

    public void testFlipOr()
    {
        boolean foo = true;
        boolean bar = false;
        boolean baz = false;

        // flip these
        if(foo || bar)
        {
            System.out.println("1");
        }
        // flip these
        if(foo || bar || baz)
        {
            System.out.println("1");
        }
    }

    public void flipCommutative()
    {
        "bar".equals("foo");
        "foo".equalsIgnoreCase("bar");
        "foo".compareTo("bar");
    }

    public void testAssertToIf(boolean foo)
    {
        assert foo :"bar";
        assert foo ;
        assert !foo ;
    }

    public void testDetailException() throws IOException
    {
        // check that this is detailed
        try
        {
            if(true)
            {
                throw new NullPointerException();
            }
            throw new ArrayIndexOutOfBoundsException();
        }
        catch(Exception e)
        {
            System.out.println("Barangus!");
        }

        // check that this is detailed, and the exceptions sorted correctly
        try
        {
            if(true)
            {
                throw new IOException();
            }
            throw new EOFException();
        }
        catch(Exception e)
        {
            System.out.println("Barangus!");
        }

        // check that this gets no intention
        try
        {
            if(true)
            {
                throw new NullPointerException();
            }
            throw new ArrayIndexOutOfBoundsException();
        }
        catch(NullPointerException e)
        {
            System.out.println("Barangus!");
        }
        catch(ArrayIndexOutOfBoundsException e)
        {
            System.out.println("Barangus!");
        }


        // check that this doesn't get detailed
        try
        {
            if(true)
            {
                throw new IOException();
            }
            throw new EOFException();
        }
        catch(Error e)
        {
            System.out.println("Barangus!");
        }

    }

    public void testDetailMethodExceptions()
    {
        try
        {
            if(true)
            {
                foo();
            }
            throw new ArrayIndexOutOfBoundsException();
        }
        catch(Exception e)
        {
            System.out.println("Barangus!");
        }
    }

    private void foo() throws IOException
    {
        throw new IOException();
    }

    public void testSimplifyDeclaration()
    {
        // test simplifying this
        int foo[];

        // test simplifying this
        int bar[] = new int[3];

        // test simplifying this
        int[] baz[] = new int[3][];

        // test simplifying this
        int bar2[] = new int[3], bar3[] = new int[4];

        // test simplifying this
        int baz2[] = new int[3], baz3[];

        // test that this doesn't simplify
        int baz4[] = new int[3], baz5;
    }

    public void testSimplifyParam(int foo[], int[] bar[], int[] baz)
    {

    }

    public void testConstantExpression()
    {
        int x = 60 * 60;
    }
    public void testConstantSubExpression()
    {
        int x = 3 * 60 * 60;
    }
    public void testConditionalDeclaration()
    {
        boolean foo = bar()?true:false;
    }

    public boolean bar()
    {
        return true;
    }

    public void testConcatenation()
    {
        System.out.println("foo" + "bar" + "baz");
        final String fooString = "foo";
        System.out.println(fooString + "bar" + "baz");
        System.out.println(fooString + "bar" + 'b');
        System.out.println(fooString + 1 + 2);
        System.out.println((fooString + 1) + 2);
        System.out.println(fooString + (1 + 2));
        final StringBuffer buffer = new StringBuffer();
        buffer.append("foo" + "bar" + "baz");
    }

    public void testStringBufferSequencing()
    {
        StringBuffer buf = new StringBuffer();
        buf.append("foo").append("bar").append("baz");
        StringBuffer buf2;
        buf2 = new StringBuffer().append("foo").append("bar").append("baz");
        final StringBuffer buf3 = new StringBuffer().append("foo").append("bar").append("baz");
    }

    public boolean testExpandBoolean()
    {
        boolean foo = true;
        boolean bar  = false;
        bar = foo;
        return bar;
    }

    public void testSplitElseIf()
    {
        if (bar()) {
            System.out.println("1");
        } else if (!bar()) {
            System.out.println("2");
        }

        if(bar())
        {
            System.out.println("1");
        }
        else if(!bar())
        {
            System.out.println("2");
        }
        else if(!bar() && bar())
        {
            System.out.println("3");
        }

        if(bar())
        {
            System.out.println("1");
        }
        else
        {
            System.out.println("2");
        }
    }

    public void testCharToString()
    {
         String foo = "b" + 'c';
         foo +='d';
        StringBuffer bar = new StringBuffer();
        bar.append('c');
        bar.append('\'');
        bar.append('"');
    }

    public void testStringToChar()
    {
         String foo = "b" + "foo";
         foo +="d";
        StringBuffer bar = new StringBuffer();
        bar.append("c");
        bar.append("\'");
        bar.append("\"");
    }

    public void testMergeParallelIfs()
    {
        boolean b = bar();
        if(b)
            System.out.println("1");
        if(b)
            System.out.println("2");
        else
            System.out.println("3");
    }

    public void testMergeParallelIfsCascaded()
    {
        boolean b = bar();
        boolean c = bar();
        if(b)
            System.out.println("1");
        else if(c)
            System.out.println("4");
        if(b)
            System.out.println("2");
        else if(c)
            System.out.println("5");
        else
            System.out.println("3");
    }

    public void testConvertToOldForLoop()
    {
        List<String> locations = new ArrayList<String>();
        for (final String location1 : locations) {
            System.out.println(location1);
        }
    }
    public void testMergeParallelIfsWitDecls()
    {
        boolean b = bar();
        if(b)
        {
            int i;
            System.out.println("1");
        }
        if(b)
        {
            int j;
            System.out.println("2");
        }else
            System.out.println("3");
    }

    private void assertNull(Object s, String value)
    {
        boolean a = bar();
        boolean b = bar();
        if(!(((a || b))))
        {
            // Do something
        }
    }

    public void testMergeForLoops()
    {
        int a = 3;
        for (int i = 0; i < a; i++) {
            System.out.println(i);
        }
        for (int i = 0; i < a; i++)
            System.out.println(i * 2);
    }

    private void assertNull(Object value)
    {
    }

    private void assertEquals(String s, Object b, Object foo)
    {
    }

    private void assertEquals(Object b, Object foo)
    {
    }

    private void assertEquals(String s, boolean b, boolean foo)
    {
    }

    private void assertEquals(boolean b, boolean foo)
    {
    }

    private void assertTrue(String message, boolean b)
    {
    }

    private void assertTrue(boolean b)
    {
    }

    private void assertFalse(String message, boolean b)
    {
    }

    private void assertFalse(boolean b)
    {
    }

    private void assertEquals(int int1, int int2)
    {
    }

    private void assertEquals(String message, int int1, int int2)
    {
    }
}
