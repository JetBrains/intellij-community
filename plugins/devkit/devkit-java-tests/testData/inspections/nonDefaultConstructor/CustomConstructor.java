class CustomConstructor extends com.intellij.openapi.extensions.<error descr="'com.intellij.openapi.extensions.AbstractExtensionPointBean' is not public in 'com.intellij.openapi.extensions'. Cannot be accessed from outside package">AbstractExtensionPointBean</error> {
  <error descr="Bean extension class should not have constructor with parameters">CustomConstructor(String foo) {

  }</error>
}