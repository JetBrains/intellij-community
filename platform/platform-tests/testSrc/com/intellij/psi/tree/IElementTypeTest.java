/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.tree;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author gregsh
 */
public class IElementTypeTest extends LightPlatformCodeInsightFixtureTestCase {

  // load all parser definitions, instantiate all lexers & parsers to initialize all IElementType constants
  @SuppressWarnings("UnusedDeclaration")
  public void testCount() throws Exception {
    int count = IElementType.getAllocatedTypesCount();
    System.out.println("Preloaded: " + count +" element types");
    LanguageExtensionPoint[] extensions = Extensions.getExtensions(new ExtensionPointName<LanguageExtensionPoint>("com.intellij.lang.parserDefinition"));
    System.out.println("ParserDefinitions: " + extensions.length);

    THashMap<Language, String> languageMap = new THashMap<>();
    languageMap.put(Language.ANY, "platform");
    final TObjectIntHashMap<String> map = new TObjectIntHashMap<>();
    for (LanguageExtensionPoint e : extensions) {
      String key = e.getPluginDescriptor().getPluginId().getIdString();
      int curCount = IElementType.getAllocatedTypesCount();
      ParserDefinition definition = (ParserDefinition)e.getInstance();
      IFileElementType type = definition.getFileNodeType();
      Language language = type.getLanguage();
      languageMap.put(language, key);
      if (language.getBaseLanguage() != null && !languageMap.containsKey(language.getBaseLanguage())) {
        languageMap.put(language.getBaseLanguage(), key);
      }
      try {
        Lexer lexer = definition.createLexer(getProject());
        PsiParser parser = definition.createParser(getProject());
      }
      catch (UnsupportedOperationException e1) {
      }

      // language-based calculation: per-class-loading stuff commented
      //int diff = IElementType.getAllocatedTypesCount() - curCount;
      //map.put(key, map.get(key) + diff);
    }
    // language-based calculation
    count = IElementType.getAllocatedTypesCount();

    for (short i = 0; i < count; i ++ ) {
      IElementType type = IElementType.find(i);
      Language language = type.getLanguage();
      String key = null;
      for (Language cur = language; cur != null && key == null; cur = cur.getBaseLanguage()) {
        key = languageMap.get(cur);
      }
      key = StringUtil.notNullize(key, "unknown");
      map.put(key, map.get(key) + 1);
      //if (key.equals("unknown")) System.out.println(type +"   " + language);
    }
    System.out.println("Total: " + IElementType.getAllocatedTypesCount() +" element types");

    // Show per-plugin statistics
    Object[] keys = map.keys();
    Arrays.sort(keys, (o1, o2) -> map.get((String)o2) - map.get((String)o1));
    int sum = 0;
    for (Object key : keys) {
      int value = map.get((String)key);
      if (value == 0) continue;
      sum += value;
      System.out.println("  " + key + ": " + value);
    }

    // leave some index-space for plugin developers
    assertTrue(IElementType.getAllocatedTypesCount() < 10000);
    assertEquals(IElementType.getAllocatedTypesCount(), sum);

    // output on 11.05.2012
    //   Preloaded: 3485 types
    //   95 definitions
    //   Total: 7694 types

    // 14.05.2015:
    // Preloaded: 4106 element types
    // ParserDefinitions: 128
    // Total: 8864 element types

    // 19.04.2016:
    // Preloaded: 4397 element types
    // ParserDefinitions: 135
    // Total: 9231 element types
  }

  public void testManipulatorRegistered() {
    LanguageExtensionPoint[] extensions =
      Extensions.getExtensions(new ExtensionPointName<LanguageExtensionPoint>("com.intellij.lang.parserDefinition"));
    Set<String> classes = new HashSet<>();
    List<String> failures = new ArrayList<>();
    int total = 0;
    for (LanguageExtensionPoint e : extensions) {
      ParserDefinition definition = (ParserDefinition)e.getInstance();

      for (IElementType type : IElementType.enumerate(IElementType.TRUE)) {
        if (type instanceof ILeafElementType) continue;
        try {
          CompositeElement treeElement = ASTFactory.composite(type);
          total++;
          PsiElement element = treeElement instanceof PsiElement? (PsiElement)treeElement : definition.createElement(treeElement);
          if (element instanceof PsiLanguageInjectionHost && classes.add(element.getClass().getName())) {
            boolean ok = ElementManipulators.getManipulator(element) != null;
            System.out.println((ok ? "OK  " : "FAIL") + " " + element.getClass().getSimpleName() + " [" + definition.getClass().getSimpleName() + "]");
            if (!ok) failures.add(element.getClass().getName());
          }
        }
        catch (Throwable ignored) {
        }
      }
    }
    System.out.println("count: " + classes.size() + ", total: " + total);
    assertEmpty("PsiLanguageInjectionHost requires " + ElementManipulators.EP_NAME, failures);
  }

  public void testInitialRegisterPerformance() {
    PlatformTestUtil.startPerformanceTest("IElementType add", 100, () -> {
      Language language = Language.ANY;
      IElementType[] old = IElementType.push(IElementType.EMPTY_ARRAY);
      try {
        for (short i = 0; i < 15000; i++) {
          IElementType type = new IElementType("i " + i, language);
          assertEquals(i, type.getIndex());
        }
      }
      finally {
        IElementType.push(old);
      }
    }).useLegacyScaling().assertTiming();
  }
}
