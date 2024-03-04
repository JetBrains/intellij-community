package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

fun Driver.replaceIdeBrowser(): FakeBrowser {
  val fakeBrowser = new(FakeBrowser::class)
  utility(ApplicationManagerRef::class)
    .getApplication()
    .replaceServiceInstance(utility(ClassRef::class).forName("com.intellij.ide.browsers.BrowserLauncher"),
                            fakeBrowser,
                            utility(DisposerRef::class).newDisposable())
  return fakeBrowser
}

@Remote("com.jetbrains.performancePlugin.FakeBrowser", plugin = "com.jetbrains.performancePlugin")
interface FakeBrowser {
  fun getLatestUrl(): String?
}

@Remote("com.intellij.openapi.application.ApplicationManager")
interface ApplicationManagerRef {
  fun getApplication(): ApplicationImplRef
}

@Remote("com.intellij.openapi.application.impl.ApplicationImpl")
interface ApplicationImplRef {
  fun replaceServiceInstance(cls: ClassRef, instance: Any, parentDisposable: DisposableRef)
}

@Remote("com.intellij.openapi.util.Disposer")
interface DisposerRef {
  fun newDisposable(): DisposableRef
}

@Remote("com.intellij.openapi.Disposable")
interface DisposableRef

@Remote("java.lang.Class")
interface ClassRef {
  fun forName(cls: String): ClassRef
}
