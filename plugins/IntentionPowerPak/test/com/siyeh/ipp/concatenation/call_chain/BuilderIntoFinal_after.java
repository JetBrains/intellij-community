class Test {
  final String X;

  {
      StringBuilder stringBuilder<caret> = new StringBuilder();
      stringBuilder.append("foo");
      stringBuilder.append(1);
      stringBuilder.append("bar");
      X = stringBuilder.toString();
  }
}