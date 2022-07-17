class SuspicousRegexExpressionArgument {{

  "a.s.d.f".split(<warning descr="Suspicious regex expression \".\" in call to 'split()'"><caret>"."</warning>);
  "vb|amna".replaceAll(<warning descr="Suspicious regex expression \"|\" in call to 'replaceAll()'">"|"</warning>, "-");
  "1+2+3".split("<error descr="Dangling quantifier '+'">+</error>");
  "one two".split(" ");
  "[][][]".split("]");
  "{}{}{}".split("}");
}}