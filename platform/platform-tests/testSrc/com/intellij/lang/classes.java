// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import org.jetbrains.annotations.NotNull;

interface TestLangExtension {
}

class MyTestExtension implements TestLangExtension {
}

class MyMetaExtension implements TestLangExtension {
}

class MyBaseExtension implements TestLangExtension {
}

class MyBaseLanguage extends Language {

  public static final Language INSTANCE = new MyBaseLanguage();

  public MyBaseLanguage() {
    super("LB");
  }
}

class MyTestLanguage extends Language {

  public static final Language INSTANCE = new MyTestLanguage();

  private MyTestLanguage() {
    super(MyBaseLanguage.INSTANCE, "L1");
  }
}

class MyMetaLanguage extends MetaLanguage {

  public static final MetaLanguage INSTANCE = new MyMetaLanguage();

  private MyMetaLanguage() {
    super("M1");
  }

  @Override
  public boolean matchesLanguage(@NotNull Language language) {
    return language == MyTestLanguage.INSTANCE;
  }
}
