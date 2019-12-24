class SuspicousRegexExpressionArgument {{

  "a.s.d.f".split(<warning descr="Suspicious regex expression \".\" in call to 'split()'"><caret>"."</warning>);
  "vb|amna".replaceAll(<warning descr="Suspicious regex expression \"|\" in call to 'replaceAll()'">"|"</warning>, "-");
  "1+2+3".split(<warning descr="Suspicious regex expression \"+\" in call to 'split()'">"<error descr="Dangling metacharacter">+</error>"</warning>);
  "one two".split(" ");
  "[][][]".split("]");
  "{}{}{}".split("}");
}}