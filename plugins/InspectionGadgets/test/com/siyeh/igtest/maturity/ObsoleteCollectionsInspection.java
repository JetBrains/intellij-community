package com.siyeh.igtest.maturity;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class ObsoleteCollectionsInspection
{
    private Vector m_foo;
    private Hashtable m_bar;
    public ObsoleteCollectionsInspection()
    {
    }

    public void foo() throws IOException
    {
        List foo = new Vector(3);
        Map bar = new Hashtable(3);

    }
}