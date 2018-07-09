// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang

import com.intellij.openapi.extensions.*
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import groovy.transform.CompileStatic

import static com.intellij.openapi.extensions.impl.ExtensionComponentAdapterTest.readElement

@CompileStatic
class LanguageExtensionOrderTest extends PlatformTestCase {

  private PluginDescriptor myDescriptor = new DefaultPluginDescriptor(PluginId.getId(""), getClass().classLoader)
  private ExtensionsArea myArea
  private LanguageExtension myLanguageExtension

  void setUp() {
    super.setUp()
    myArea = Extensions.rootArea
    myLanguageExtension = new LanguageExtension<TestLangExtension>("langExt")
    registerMetaLanguage()
    registerLanguageEP()
  }

  private void registerMetaLanguage() {
    PlatformTestUtil.registerExtension myArea, MetaLanguage.EP_NAME, MyMetaLanguage.INSTANCE, testRootDisposable
  }

  private void registerLanguageEP() {
    myArea.registerExtensionPoint myDescriptor, readElement('''\
<extensionPoint qualifiedName="langExt" beanClass="com.intellij.lang.LanguageExtensionPoint">
  <with attribute="implementationClass" implements="com.intellij.lang.TestLangExtension"/>
</extensionPoint>    
''')
    Disposer.register(testRootDisposable) {
      myArea.unregisterExtensionPoint("langExt")
    }
  }

  private void registerExtensions(String... xmls) {
    for (ext in xmls) {
      myArea.registerExtension(myDescriptor, readElement(ext), null)
    }
  }

  private void doTest(Class<?>... classes) {
    def extensions = myLanguageExtension.allForLanguage(MyTestLanguage.INSTANCE)
    assert extensions.size() == classes.length
    def extensionClasses = extensions.collect { it.class }
    assert extensionClasses == classes
  }

  void 'test language before base-language'() {
    registerExtensions(
      '<extension point="langExt" language="LB" implementationClass="com.intellij.lang.MyBaseExtension"/>',
      '<extension point="langExt" language="L1" implementationClass="com.intellij.lang.MyTestExtension" order="first"/>'
    )
    doTest MyTestExtension, MyBaseExtension
  }

  void 'test meta-language before language'() {
    registerExtensions(
      '<extension point="langExt" language="L1" implementationClass="com.intellij.lang.MyTestExtension" id="default"/>',
      '<extension point="langExt" language="M1" implementationClass="com.intellij.lang.MyMetaExtension" order="before default"/>'
    )
    doTest MyMetaExtension, MyTestExtension
  }

  void 'test meta-language before base-language'() {
    registerExtensions(
      '<extension point="langExt" language="LB" implementationClass="com.intellij.lang.MyBaseExtension"/>',
      '<extension point="langExt" language="M1" implementationClass="com.intellij.lang.MyMetaExtension" order="last"/>'
    )
    doTest MyMetaExtension, MyBaseExtension
  }
}
