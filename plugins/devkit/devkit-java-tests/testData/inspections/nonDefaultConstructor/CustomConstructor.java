class CustomConstructor extends com.intellij.codeInsight.completion.CompletionContributor {
  <error descr="Extension should not have constructor with parameters">CustomConstructor</error>(String foo) {

  }
}