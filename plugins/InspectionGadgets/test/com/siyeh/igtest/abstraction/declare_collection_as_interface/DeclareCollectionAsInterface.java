package com.siyeh.igtest.abstraction.declare_collection_as_interface;

import java.util.*;

public class DeclareCollectionAsInterface
{
    private HashMap<String, String> m_mapThree = new HashMap<String, String>(2);
    private <warning descr="Declaration of 'HashMap' should probably be weakened to 'java.util.Map'">HashMap</warning> m_setOne = new HashMap(2);
    private Map m_setTwo = new HashMap(2);

    public DeclareCollectionAsInterface()
    {
        m_setOne.put("foo", "foo");
        m_setTwo.put("bar", "bar");
    }

    public void fooBar()
    {
        final <warning descr="Declaration of 'HashMap' should probably be weakened to 'java.util.Map'">HashMap</warning> map1 = new HashMap(2);
        final Map map2 = new HashMap(2);
        map1.put("foo", "foo");
        map2.put("bar", "bar");
    }

    public void fooBaz(<warning descr="Declaration of 'HashMap' should probably be weakened to 'java.util.Map'">HashMap</warning> set1, Map set2)
    {
        set1.put("foo", "foo");
        set2.put("bar", "bar");
    }

    public HashMap fooBaz()
    {
        return new HashMap();
    }

    void writeContent() {
        final <warning descr="Declaration of 'HashMap' should probably be weakened to 'java.util.Map'">HashMap</warning><String, Object> templateMap = new HashMap();
        processTemplate(templateMap);
    }

    void processTemplate(Object o) {}

    void foo() {
        Object o = theRoad();
    }

    <warning descr="Declaration of 'HashMap' should probably be weakened to 'java.util.Map'">HashMap</warning> theRoad() {
        return null;
    }

    void makeItRight() {
      <warning descr="Declaration of 'ArrayList' should probably be weakened to 'java.util.List'">ArrayList</warning> list22 = new ArrayList();
      System.out.println(list22.get(0));

      ArrayList<String> list33 = new ArrayList();
      System.out.println(list33.get(0));
    }

  void inOrderOfAbstraction() {
    <warning descr="Declaration of 'HashSet' should probably be weakened to 'java.util.Set'">HashSet</warning><String> set = new HashSet<>();
    set.add("foo");
  }

  public static Properties stringToProperties(String propertiesAsString) { return null; }
  public static Properties stringToProperties2(String propertiesAsString) { return null; }
  public static Properties stringToProperties3(String propertiesAsString) { return null; }

  void m() {
    stringToProperties("");
    <error descr="Incompatible types. Found: 'java.util.Properties', required: 'java.lang.String'">String s = stringToProperties2("");</error>
    <error descr="Incompatible types. Found: 'java.util.Properties', required: 'java.lang.String'">s = stringToProperties3("")</error>;
  }

  private Properties properties;
  public void setProperties(Properties properties) {
    if (properties == null) {
      this.properties = new Properties();
    } else {
      this.properties = (Properties) properties.clone();
    }
  }
}
