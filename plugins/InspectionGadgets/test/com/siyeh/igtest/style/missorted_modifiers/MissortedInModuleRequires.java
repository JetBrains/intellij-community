module sample {
  requires <warning descr="Missorted modifiers 'transitive static'">transitive<caret></warning> static <error descr="Module not found: x">x</error>;
}