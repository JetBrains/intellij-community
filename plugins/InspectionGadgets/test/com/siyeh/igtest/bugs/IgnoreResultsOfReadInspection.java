package com.siyeh.igtest.bugs;

import java.io.*;

public class IgnoreResultsOfReadInspection
{
    private void foo()
    {
        try
        {
            final FileInputStream stream = new FileInputStream("FOO");
            final byte[] buffer = new byte[10];
            stream.read(buffer);
        }
        catch(FileNotFoundException e)
        {
        }
        catch(IOException e)
        {
        }

    }
}
