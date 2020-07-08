// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Element;

/**
 * @author Bas Leijdekkers
 */
public class MatchVariableConstraintTest extends LightPlatformTestCase {

  public void testConvertRegExpToTypeString() {
    assertEquals("convert array types correctly",
                 "char[]|int[]", MatchVariableConstraint.convertRegExpTypeToTypeString("char\\[]|int\\[\\]"));
    assertEquals("consider dots without following meta chars a regular dot (common mistake)",
                 "java.lang.String", MatchVariableConstraint.convertRegExpTypeToTypeString("java.lang.String"));
    assertEquals("", MatchVariableConstraint.convertRegExpTypeToTypeString("start.*"));
    assertEquals("parentheses", "int|long", MatchVariableConstraint.convertRegExpTypeToTypeString("(int|long)"));
  }

  public void testConvertTypeStringToRegExp() {
    assertEquals("char\\[\\]|int\\[\\]", MatchVariableConstraint.convertTypeStringToRegExp("char[]|int[]"));
  }

  public void testWriteExternal() {
    final MatchVariableConstraint constraint = new MatchVariableConstraint();
    constraint.setName("elephant");
    constraint.setNameOfExprType("String");
    constraint.setNameOfFormalArgType("String");
    final Element test = new Element("constraint");
    constraint.writeExternal(test);
    assertEquals("<constraint name=\"elephant\" nameOfExprType=\"String\" nameOfFormalType=\"String\" within=\"\" contains=\"\" />",
                 JDOMUtil.writeElement(test));
  }

  public void testAdditionalConstraints1() {
    final MatchVariableConstraint constraint = new MatchVariableConstraint();
    assertNull(constraint.getAdditionalConstraint("hypergolic"));
    try {
      constraint.putAdditionalConstraint("reference", "test");
      fail();
    } catch (IllegalArgumentException ignored) {}
    try {
      constraint.putAdditionalConstraint("Capital", "test");
      fail();
    } catch (IllegalArgumentException ignored) {}
    try {
      constraint.putAdditionalConstraint("words with spaces", "test");
      fail();
    } catch (IllegalArgumentException ignored) {}

    constraint.putAdditionalConstraint("test", "test");
    assertFalse(constraint.equals(new MatchVariableConstraint()));

    final Element element = new Element("constraint");
    constraint.writeExternal(element);
    assertEquals("<constraint name=\"\" within=\"\" contains=\"\" test=\"test\" />",
                 JDOMUtil.writeElement(element));
    final MatchVariableConstraint constraint2 = new MatchVariableConstraint();
    constraint2.readExternal(element);
    assertEquals("test", constraint2.getAdditionalConstraint("test"));

    constraint.putAdditionalConstraint("test",null);
    assertTrue(constraint.equals(new MatchVariableConstraint()));
  }

}