package com.siyeh.igtest.abstraction.declare_collection_as_interface;

import java.util.*;

public class DeclareCollectionAsInterface
{
    private HashMap<String, String> m_mapThree = new HashMap<String, String>(2);
    private HashMap m_setOne = new HashMap(2);
    private Map m_setTwo = new HashMap(2);

    public DeclareCollectionAsInterface()
    {
        m_setOne.put("foo", "foo");
        m_setTwo.put("bar", "bar");
    }

    public void fooBar()
    {
        final HashMap map1 = new HashMap(2);
        final Map map2 = new HashMap(2);
        map1.put("foo", "foo");
        map2.put("bar", "bar");
    }

    public void fooBaz(HashMap set1, Map set2)
    {
        set1.put("foo", "foo");
        set2.put("bar", "bar");
    }

    public HashMap fooBaz()
    {
        return new HashMap();
    }

    void writeContent() {
        final HashMap<String, Object> templateMap = new HashMap();
        processTemplate(templateMap);
    }

    void processTemplate(Object o) {}

    void foo() {
        Object o = theRoad();
    }

    HashMap theRoad() {
        return null;
    }

    void makeItRight() {
      ArrayList list22 = new ArrayList();
      System.out.println(list22.get(0));

      ArrayList<String> list33 = new ArrayList();
      System.out.println(list33.get(0));
    }
}
