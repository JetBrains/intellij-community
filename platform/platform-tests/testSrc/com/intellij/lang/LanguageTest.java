// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import org.junit.Test;

public class LanguageTest {
  @Test
  public void testTransitiveDialects() {
    class MyLang extends Language {
      private MyLang() {
        super("MyTestLangID");
      }
    }
    MyLang MY_LANG = new MyLang();
    class MyLangDialect1 extends Language {
      private MyLangDialect1() {
        super(MY_LANG, "MyLangDialect1");
      }
    }
    MyLangDialect1 MY_LANG_DIALECT1 = new MyLangDialect1();
    class MyLangDialect11 extends Language {
      private MyLangDialect11() {
        super(MY_LANG_DIALECT1, "MyLangDialect11");
      }
    }
    MyLangDialect11 MY_LANG_DIALECT11 = new MyLangDialect11();

    try {
      UsefulTestCase.assertSameElements(MY_LANG.getDialects(), MY_LANG_DIALECT1);
      UsefulTestCase.assertSameElements(MY_LANG_DIALECT1.getDialects(), MY_LANG_DIALECT11);
      UsefulTestCase.assertSameElements(MY_LANG_DIALECT11.getDialects());

      UsefulTestCase.assertSameElements(MY_LANG.getTransitiveDialects(), MY_LANG_DIALECT1, MY_LANG_DIALECT11);
      UsefulTestCase.assertSameElements(MY_LANG_DIALECT1.getTransitiveDialects(), MY_LANG_DIALECT11);
      UsefulTestCase.assertSameElements(MY_LANG_DIALECT11.getTransitiveDialects());

      MY_LANG_DIALECT11.unregisterLanguage(new DefaultPluginDescriptor(PluginManagerCore.CORE_PLUGIN_ID));
      UsefulTestCase.assertSameElements(MY_LANG.getDialects(), MY_LANG_DIALECT1);
      UsefulTestCase.assertSameElements(MY_LANG_DIALECT1.getDialects());

      UsefulTestCase.assertSameElements(MY_LANG.getTransitiveDialects(), MY_LANG_DIALECT1);
      UsefulTestCase.assertSameElements(MY_LANG_DIALECT1.getTransitiveDialects());


      class MyLangDialect12 extends Language {
        private MyLangDialect12() {
          super("MyLangDialect12");
        }
      }
      MyLangDialect12 MY_LANG_DIALECT12 = new MyLangDialect12();
      MY_LANG_DIALECT1.registerDialect(MY_LANG_DIALECT12);
      UsefulTestCase.assertSameElements(MY_LANG.getDialects(), MY_LANG_DIALECT1);
      UsefulTestCase.assertSameElements(MY_LANG_DIALECT1.getDialects(), MY_LANG_DIALECT12);

      UsefulTestCase.assertSameElements(MY_LANG.getTransitiveDialects(), MY_LANG_DIALECT1, MY_LANG_DIALECT12);
      UsefulTestCase.assertSameElements(MY_LANG_DIALECT1.getTransitiveDialects(), MY_LANG_DIALECT12);

      MY_LANG_DIALECT12.unregisterLanguage(new DefaultPluginDescriptor(PluginManagerCore.CORE_PLUGIN_ID));
      // MyLangDialect12 might leak because of leaky "MY_LANG_DIALECT1.registerDialect(MY_LANG_DIALECT12);" API, so the manual "unregisterDialect" is needed
      MY_LANG_DIALECT1.unregisterDialect(MY_LANG_DIALECT12);

      MY_LANG_DIALECT1.unregisterLanguage(new DefaultPluginDescriptor(PluginManagerCore.CORE_PLUGIN_ID));
      UsefulTestCase.assertSameElements(MY_LANG.getDialects());

      UsefulTestCase.assertSameElements(MY_LANG.getTransitiveDialects());
    }
    finally {
      MY_LANG.unregisterLanguage(new DefaultPluginDescriptor(PluginManagerCore.CORE_PLUGIN_ID));
    }
  }
}

