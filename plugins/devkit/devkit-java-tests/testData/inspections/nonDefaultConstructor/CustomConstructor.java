class CustomConstructor extends com.intellij.codeInsight.completion.CompletionContributor {
  <error descr="Bean extension class should not have constructor with parameters">CustomConstructor(String foo) {

  }</error>
}