package com.siyeh.igtest.abstraction.declare_collection_as_interface;

import java.util.*;

public class DeclareCollectionsAsInterfaceInspection
{
    private HashSet<String> m_setThree = new HashSet<String>(2);
    private HashSet m_setOne = new HashSet(2);
    private Set m_setTwo = new HashSet(2);

    public DeclareCollectionsAsInterfaceInspection()
    {
        m_setOne.add("foo");
        m_setTwo.add("bar");
    }

    public void fooBar()
    {
        final HashSet set1 = new HashSet(2);
        final Set set2 = new HashSet(2);
        set1.add("foo");
        set2.add("bar");
    }

    public void fooBaz(TreeSet set1, Set set2)
    {
        set1.add("foo");
        set2.add("bar");
    }

    public HashSet fooBaz()
    {
        return new HashSet();
    }

    void writeContent() {
        final HashMap<String, Object> templateMap = new HashMap();
        processTemplate(templateMap);
    }

    void processTemplate(Object o) {}
}
