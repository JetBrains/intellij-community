class CustomConstructor extends com.intellij.codeInsight.completion.CompletionContributor {
  <error descr="Extension should not have constructor with parameters.
Do not instantiate services in constructor because they should be requested only when needed.">CustomConstructor</error>(String foo) {

  }
}