package com.siyeh.igtest.controlflow.overly_complex_boolean_expression;

public class OverlyComplexBooleanExpression {

  boolean x(boolean b) {
    return b && b || b && b;
  }

  boolean ignore(boolean b) {
    return b || b || b || b;
  }

}