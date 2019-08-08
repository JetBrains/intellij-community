class CustomConstructor extends com.intellij.codeInsight.completion.CompletionContributor {
  <error descr="Extension should not have constructor with parameters. To not instantiate services in constructor.">CustomConstructor(String foo) {

  }</error>
}