package com.siyeh.igtest.exceptionHandling;

public class ThrowCaughtLocallInspection
{
    public void foo() throws Exception
    {
        try {
            throw new MyCheckedException();
        } catch (MyCheckedException e) {
            e.printStackTrace(); //TODO
        }
        try {
            throw new MyCheckedException();
        } catch (Exception e) {
            e.printStackTrace(); //TODO
        }

        try {
            throw new MyCheckedException();
        } catch (MyUncheckedException e) {
            if (false) {
                throw new MyUncheckedException(e);
            } else {
            }
        }

        try {
            try {
                throw new MyCheckedException();
            } catch (MyUncheckedException e) {
                e.printStackTrace(); //TODO

            }
        } catch (MyCheckedException e) {
            e.printStackTrace(); //TODO
        }
    }


}
