package com.siyeh.igtest.portability;

import java.io.IOException;

public class HardcodedLineSeparatorsInspection
{
    public HardcodedLineSeparatorsInspection()
    {
    }

    public void foo() throws IOException
    {
        final String newlineString = "\n";
        final String returnString = "\r";
    }
}