package com.siyeh.igtest.controlflow.negated_conditional_expression;

class NegatedConditionalExpression {

    String myField1 = null;
    String myField2 = null;

    boolean x(NegatedConditionalExpression myClass) {
      System.out.println(!(myClass.myField2 != null));
      System.out.println(myField2 != null ? !myField2.equals(myClass.myField2) : myClass.myField2 != null);
      final boolean b = myField1 != null ;
      return <warning descr="Negating conditional expression">!</warning>(myField2 != null ? !myField2.equals(myClass.myField2) : myClass.myField2 != null);
    }
}