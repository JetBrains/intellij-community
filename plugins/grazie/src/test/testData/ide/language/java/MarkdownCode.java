/// Some <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Spelling.MULTI_WORD">javadoc</GRAMMAR_ERROR>. Maybe with some <TYPO descr="Typo: In word 'grammr'">grammr</TYPO> and, punctuation, <TYPO descr="Typo: In word 'mistaks'">mistaks</TYPO>.
///
/// ```
/// void main(String[] args){
///   var a = args.length == 1 ? new ArrayList<String>() : new LinkedList<String>();
///}
///```

/// Returns all [PsiClass]es annotated with `@io.cucumber.java.StepDefinitionAnnotation` (or similar).
///
/// In practice, this method returns a single list of all language-specific step definition annotations
/// - English: `@io.cucumber.java.en.Given`, `@io.cucumber.java.en.When`, `@io.cucumber.java.en.Then`
/// - Polish: `@io.cucumber.java.pl.Zakładając`, `@io.cucumber.java.pl.Jeżeli`, `@io.cucumber.java.pl.Wtedy`
/// - and so on