class X {

  String s = """
\"""";
  String t = """
<warning descr="'\\\\"' is unnecessarily escaped"><caret>\"</warning> """;
  String u = """
""\" """;
  String v = """
\""" """;
}