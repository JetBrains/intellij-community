// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.ServiceContainerUtil
import groovy.transform.CompileStatic

@CompileStatic
class LanguageExtensionOrderTest extends LightPlatformTestCase {
  private PluginDescriptor myDescriptor = new DefaultPluginDescriptor(PluginId.getId(""), getClass().classLoader)
  private ExtensionsAreaImpl myArea
  private LanguageExtension myLanguageExtension

  void setUp() {
    super.setUp()
    myArea = ApplicationManager.getApplication().getExtensionArea() as ExtensionsAreaImpl
    myLanguageExtension = new LanguageExtension<TestLangExtension>("langExt")
    registerMetaLanguage()
    registerLanguageEP()
  }

  private void registerMetaLanguage() {
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), MetaLanguage.EP_NAME, MyMetaLanguage.INSTANCE, testRootDisposable)
  }

  private void registerLanguageEP() {
    myArea.registerExtensionPoints(myDescriptor, Collections.singletonList(JDOMUtil.load('''\
    <extensionPoint qualifiedName="langExt" beanClass="com.intellij.lang.LanguageExtensionPoint">
      <with attribute="implementationClass" implements="com.intellij.lang.TestLangExtension"/>
    </extensionPoint>    
    ''')))
    Disposer.register(testRootDisposable) {
      myArea.unregisterExtensionPoint("langExt")
    }
  }

  private void registerExtensions(String... xmls) {
    for (ext in xmls) {
      myArea.registerExtension(myDescriptor, JDOMUtil.load(ext), null)
    }
  }

  private void doTest(Class<?>... classes) {
    def extensions = myLanguageExtension.allForLanguage(MyTestLanguage.INSTANCE)
    assert extensions.size() == classes.length
    def extensionClasses = extensions.collect { it.class }
    assert extensionClasses == Arrays.asList(classes)
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
