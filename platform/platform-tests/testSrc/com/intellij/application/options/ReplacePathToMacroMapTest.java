// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReplacePathToMacroMapTest {
  private ReplacePathToMacroMap myMap;

  @Before
  public final void setupMap() {
    myMap = new ReplacePathToMacroMap();
    myMap.addMacroReplacement("/tmp/foo", "MODULE_DIR");
  }

  @Test
  public void testSubstitute_NoSubstitute() {
    assertEquals("/foo/bar", substitute("/foo/bar"));
  }

  @Test
  public void testSubstitute_CompleteMatch() {
    assertEquals("$MODULE_DIR$", substitute("/tmp/foo"));
    assertEquals("jar:$MODULE_DIR$", substitute("jar:/tmp/foo"));
    assertEquals("jar:/$MODULE_DIR$", substitute("jar://tmp/foo"));
    assertEquals("jar://$MODULE_DIR$", substitute("jar:///tmp/foo"));
    assertEquals("file:$MODULE_DIR$", substitute("file:/tmp/foo"));
    assertEquals("file:/$MODULE_DIR$", substitute("file://tmp/foo"));
    assertEquals("file://$MODULE_DIR$", substitute("file:///tmp/foo"));
  }

  @Test
  public void testSubstitute_PrefixMatch() {
    assertEquals("$MODULE_DIR$/bar", substitute("/tmp/foo/bar"));
    assertEquals("jar:$MODULE_DIR$/bar", substitute("jar:/tmp/foo/bar"));
    assertEquals("jar:/$MODULE_DIR$/bar", substitute("jar://tmp/foo/bar"));
    assertEquals("jar://$MODULE_DIR$/bar", substitute("jar:///tmp/foo/bar"));
    assertEquals("file:$MODULE_DIR$/bar", substitute("file:/tmp/foo/bar"));
    assertEquals("file:/$MODULE_DIR$/bar", substitute("file://tmp/foo/bar"));
    assertEquals("file://$MODULE_DIR$/bar", substitute("file:///tmp/foo/bar"));
  }

  @Test
  public void testSubstitute_JarPrefixMatch() {
    assertEquals("$MODULE_DIR$!/bar", substitute("/tmp/foo!/bar"));
    assertEquals("jar:$MODULE_DIR$!/bar", substitute("jar:/tmp/foo!/bar"));
    assertEquals("jar:/$MODULE_DIR$!/bar", substitute("jar://tmp/foo!/bar"));
    assertEquals("jar://$MODULE_DIR$!/bar", substitute("jar:///tmp/foo!/bar"));
    assertEquals("file:$MODULE_DIR$!/bar", substitute("file:/tmp/foo!/bar"));
    assertEquals("file:/$MODULE_DIR$!/bar", substitute("file://tmp/foo!/bar"));
    assertEquals("file://$MODULE_DIR$!/bar", substitute("file:///tmp/foo!/bar"));
  }

  @Test
  public void testSubstitute_WindowsRootSubstitution() {
    myMap.put("C:/", "../$MODULE_DIR$/");

    assertEquals("../$MODULE_DIR$/", substitute("C:/"));
    assertEquals("../$MODULE_DIR$/foo", substitute("C:/foo"));
  }

  @Test
  public void testSubstitute_NoSubstituteInTheMiddleOccurs() {
    assertEquals("/bar/tmp/foo/bar", substitute("/bar/tmp/foo/bar"));
  }

  @Test
  public void testSubstitute_NoDoubleSubstitution() {
    assertEquals("$MODULE_DIR$/bar/tmp/foo", substitute("/tmp/foo/bar/tmp/foo"));
  }

  @Test
  public void testSubstitute_NoPartialSubstituteIsPerformed() {
    assertEquals("/tmp/foobar", substitute("/tmp/foobar"));
  }

  @Test
  public void testSubstitute_ProperSubstitutionPriorityApplied() {
    myMap.put("/root/project/module", "$MODULE_DIR$");
    myMap.put("/root/project", "../$MODULE_DIR$");
    myMap.put("/root", "$APPLICATION_HOME_DIR$");

    assertEquals("$MODULE_DIR$", substitute("/root/project/module"));
    assertEquals("../$MODULE_DIR$", substitute("/root/project"));
    assertEquals("$APPLICATION_HOME_DIR$/appsubdir", substitute("/root/appsubdir"));
    assertEquals("$APPLICATION_HOME_DIR$", substitute("/root"));
  }

  @Test
  public void testUnknownProtocol() {
    assertEquals("unknown:/tmp/foo", substitute("unknown:/tmp/foo")); // not substituted
    assertEquals("-Dprop=unknown:$MODULE_DIR$", myMap.substituteRecursively("-Dprop=unknown:/tmp/foo", true).toString()); // substituted
  }

  private String substitute(@NotNull String s) {
    return myMap.substitute(s, true);
  }
}
