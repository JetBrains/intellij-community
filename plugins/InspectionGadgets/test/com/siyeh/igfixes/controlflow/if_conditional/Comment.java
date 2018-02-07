class Test {

  String foo(){
    i<caret>f (child instanceof KeyedValue) {
      // Handling properties of the object
      return "(id)";
    }
    else {
      // Handling ivar-s
      return "(" + myValue.getEvaluationExpression(true) + ")->" + child.getName();
    }
  }
}