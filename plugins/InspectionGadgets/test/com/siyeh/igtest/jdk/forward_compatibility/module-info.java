module M {
  requires <warning descr="Modifiers on 'requires java.base' are prohibited in releases after Java 9">static</warning> <warning descr="Modifiers on 'requires java.base' are prohibited in releases after Java 9">transitive</warning> <error descr="Module not found: java.base">java.base</error>;
}