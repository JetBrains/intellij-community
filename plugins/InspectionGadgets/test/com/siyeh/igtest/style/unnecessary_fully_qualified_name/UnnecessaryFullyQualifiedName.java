package com.siyeh.igtest.style.unnecessary_fully_qualified_name;

import java.io.PrintStream;
import java.util.Properties;

/**
 * {@link java.lang.String}
 */
public class UnnecessaryFullyQualifiedName
{
    private String m_string1;
    private java.lang.String m_string;
    private java.util.StringTokenizer m_map;
    private java.util.List m_list;
    private java.util.Map.Entry m_mapEntry;
    private java.awt.List m_awtList;
    PrintStream stream = java.lang.System.out;
    Properties props = java.lang.System.getProperties();

    public UnnecessaryFullyQualifiedNameInspection(java.lang.String s) {}

    class String {}

    java.util.Vector v;
    class Vector {}
    
    java.util.Set set;
    String Set;
}
enum SomeEnum {

    Foo {
        @Override
        public void perform() {
            test.Foo.perform();
        }
    };

    public abstract void perform();

    private java.   util.   List spaces;
}