// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Bas Leijdekkers
 */
public class MatchVariableConstraintTest {

  @Test
  public void testConvertRegExpToTypeString() {
    assertEquals("convert array types correctly",
                 "char[]|int[]", MatchVariableConstraint.convertRegExpTypeToTypeString("char\\[]|int\\[\\]"));
    assertEquals("consider dots without following meta chars a regular dot (common mistake)",
                 "java.lang.String", MatchVariableConstraint.convertRegExpTypeToTypeString("java.lang.String"));
    assertEquals("", MatchVariableConstraint.convertRegExpTypeToTypeString("start.*"));
    assertEquals("parentheses", "int|long", MatchVariableConstraint.convertRegExpTypeToTypeString("(int|long)"));
  }

  @Test
  public void testConvertTypeStringToRegExp() {
    assertEquals("char\\[\\]|int\\[\\]", MatchVariableConstraint.convertTypeStringToRegExp("char[]|int[]"));
  }

  @Test
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

}