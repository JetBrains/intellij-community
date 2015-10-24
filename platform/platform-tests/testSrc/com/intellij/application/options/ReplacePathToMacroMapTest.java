package com.intellij.application.options;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author mike
 */
public class ReplacePathToMacroMapTest {
  private ReplacePathToMacroMap myMap;

  @Before
  public final void setupMap() {
    myMap = new ReplacePathToMacroMap();
    myMap.addMacroReplacement("/tmp/foo", "MODULE_DIR");
  }

  @Test
  public void testSubstitute_NoSubstitute() throws Exception {
    assertEquals("/foo/bar", substitute("/foo/bar"));
  }

  @Test
  public void testSubstitute_CompleteMatch() throws Exception {
    assertEquals("$MODULE_DIR$", substitute("/tmp/foo"));
    assertEquals("jar:$MODULE_DIR$", substitute("jar:/tmp/foo"));
    assertEquals("jar:/$MODULE_DIR$", substitute("jar://tmp/foo"));
    assertEquals("jar://$MODULE_DIR$", substitute("jar:///tmp/foo"));
    assertEquals("file:$MODULE_DIR$", substitute("file:/tmp/foo"));
    assertEquals("file:/$MODULE_DIR$", substitute("file://tmp/foo"));
    assertEquals("file://$MODULE_DIR$", substitute("file:///tmp/foo"));
  }

  @Test
  public void testSubstitute_PrefixMatch() throws Exception {
    assertEquals("$MODULE_DIR$/bar", substitute("/tmp/foo/bar"));
    assertEquals("jar:$MODULE_DIR$/bar", substitute("jar:/tmp/foo/bar"));
    assertEquals("jar:/$MODULE_DIR$/bar", substitute("jar://tmp/foo/bar"));
    assertEquals("jar://$MODULE_DIR$/bar", substitute("jar:///tmp/foo/bar"));
    assertEquals("file:$MODULE_DIR$/bar", substitute("file:/tmp/foo/bar"));
    assertEquals("file:/$MODULE_DIR$/bar", substitute("file://tmp/foo/bar"));
    assertEquals("file://$MODULE_DIR$/bar", substitute("file:///tmp/foo/bar"));
  }

  @Test
  public void testSubstitute_JarPrefixMatch() throws Exception {
    assertEquals("$MODULE_DIR$!/bar", substitute("/tmp/foo!/bar"));
    assertEquals("jar:$MODULE_DIR$!/bar", substitute("jar:/tmp/foo!/bar"));
    assertEquals("jar:/$MODULE_DIR$!/bar", substitute("jar://tmp/foo!/bar"));
    assertEquals("jar://$MODULE_DIR$!/bar", substitute("jar:///tmp/foo!/bar"));
    assertEquals("file:$MODULE_DIR$!/bar", substitute("file:/tmp/foo!/bar"));
    assertEquals("file:/$MODULE_DIR$!/bar", substitute("file://tmp/foo!/bar"));
    assertEquals("file://$MODULE_DIR$!/bar", substitute("file:///tmp/foo!/bar"));
  }

  @Test
  public void testSubstitute_WindowsRootSubstitution() throws Exception {
    myMap.put("C:/", "../$MODULE_DIR$/");

    assertEquals("../$MODULE_DIR$/", substitute("C:/"));
    assertEquals("../$MODULE_DIR$/foo", substitute("C:/foo"));
  }

  @Test
  public void testSubstitute_NoSubstituteInTheMiddleOccurs() throws Exception {
    assertEquals("/bar/tmp/foo/bar", substitute("/bar/tmp/foo/bar"));
  }

  @Test
  public void testSubstitute_NoDoubleSubstitution() throws Exception {
    assertEquals("$MODULE_DIR$/bar/tmp/foo", substitute("/tmp/foo/bar/tmp/foo"));
  }

  @Test
  public void testSubstitute_NoPartialSubstituteIsPerformed() throws Exception {
    assertEquals("/tmp/foobar", substitute("/tmp/foobar"));
  }

  @Test
  public void testSubstitute_ProperSubstitutionPriorityApplied() throws Exception {
    myMap.put("/root/project/module", "$MODULE_DIR$");
    myMap.put("/root/project", "../$MODULE_DIR$");
    myMap.put("/root", "$APPLICATION_HOME_DIR$");

    assertEquals("$MODULE_DIR$", substitute("/root/project/module"));
    assertEquals("../$MODULE_DIR$", substitute("/root/project"));
    assertEquals("$APPLICATION_HOME_DIR$/appsubdir", substitute("/root/appsubdir"));
    assertEquals("$APPLICATION_HOME_DIR$", substitute("/root"));
  }

  private String substitute(final String s) {
    return myMap.substitute(s, true);
  }
}
