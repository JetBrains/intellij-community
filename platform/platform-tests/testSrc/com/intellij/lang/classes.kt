// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

internal interface TestLangExtension

internal class MyTestExtension : TestLangExtension

internal class MyMetaExtension : TestLangExtension

internal class MyBaseExtension : TestLangExtension

internal class MyBaseLanguage : Language("LB") {
  companion object {
    val INSTANCE: Language = MyBaseLanguage()
  }
}

internal class MyTestLanguage : Language(MyBaseLanguage.INSTANCE, "L1") {
  companion object {
    val INSTANCE: Language = MyTestLanguage()
  }
}

internal class MyTestLanguage2 : Language(MyTestLanguage.INSTANCE, "L2") {
  companion object {
    val INSTANCE: Language = MyTestLanguage2()
  }
}

internal class MyMetaLanguage : MetaLanguage("M1") {
  companion object {
    val INSTANCE: MetaLanguage = MyMetaLanguage()
  }

  override fun matchesLanguage(language: Language): Boolean {
    return language === MyTestLanguage.INSTANCE
  }
}
