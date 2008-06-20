package com.siyeh.igtest.maturity;


import javax.swing.*;
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

    private static void useObsoleteParameter() {
        Vector v = new Vector(1);
        // make some necessary actions with v
        JTable table = new JTable(v, new Vector(2));
    }
}