package com.siyeh.igtest.errorhandling.throw_from_finally_block;

import java.io.FileInputStream;
import java.io.IOException;

public class ThrowFromFinallyBlock
{
    public void foo() throws Exception
    {
        try
        {
            return;
        }
        finally
        {
            <warning descr="'throw' inside 'finally' block">throw</warning> new Exception();
        }
    }

    public void bar() throws Exception
    {
        try
        {
            return;
        }
        finally
        {
            try
            {
                <warning descr="'throw' inside 'finally' block">throw</warning> new Exception();
            }
            finally
            {
                <warning descr="'throw' inside 'finally' block">throw</warning> new Exception();
            }
        }
    }

    public void safe() throws IOException {
        try (FileInputStream in = new FileInputStream("name")) {

        } catch (RuntimeException e) {
            // ...
        } finally {
            try {
                throw new NullPointerException();
            } catch (RuntimeException e) {}
        }
    }

}
