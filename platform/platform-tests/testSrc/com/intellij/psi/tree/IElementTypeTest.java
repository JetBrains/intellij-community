// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.tree;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import gnu.trove.TObjectIntHashMap;

import java.util.*;

/**
 * @author gregsh
 */
public class IElementTypeTest extends BasePlatformTestCase {
  // load all parser definitions, instantiate all lexers & parsers to initialize all IElementType constants
  @SuppressWarnings("UnusedDeclaration")
  public void testCount() {
    int count = IElementType.getAllocatedTypesCount();
    LOG.debug("Preloaded: " + count +" element types");
    List<LanguageExtensionPoint> extensions = Extensions.getRootArea().<LanguageExtensionPoint>getExtensionPoint(LanguageParserDefinitions.INSTANCE.getName()).getExtensionList();
    LOG.debug("ParserDefinitions: " + extensions.size());

    Map<Language, String> languageMap = new HashMap<>();
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
      catch (UnsupportedOperationException ignored) {
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
    LOG.debug("Total: " + IElementType.getAllocatedTypesCount() +" element types");

    // Show per-plugin statistics
    Object[] keys = map.keys();
    Arrays.sort(keys, (o1, o2) -> map.get((String)o2) - map.get((String)o1));
    int sum = 0;
    for (Object key : keys) {
      int value = map.get((String)key);
      if (value == 0) continue;
      sum += value;
      LOG.debug("  " + key + ": " + value);
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
    Set<String> classes = new HashSet<>();
    List<String> failures = new ArrayList<>();
    int total = 0;
    for (LanguageExtensionPoint e : Extensions.getRootArea().<LanguageExtensionPoint>getExtensionPoint(LanguageParserDefinitions.INSTANCE.getName()).getExtensionList()) {
      ParserDefinition definition = (ParserDefinition)e.getInstance();

      for (IElementType type : IElementType.enumerate(IElementType.TRUE)) {
        if (type instanceof ILeafElementType) continue;
        try {
          CompositeElement treeElement = ASTFactory.composite(type);
          total++;
          PsiElement element = treeElement instanceof PsiElement? (PsiElement)treeElement : definition.createElement(treeElement);
          if (element instanceof PsiLanguageInjectionHost && classes.add(element.getClass().getName())) {
            boolean ok = ElementManipulators.getManipulator(element) != null;
            if (!ok) {
              System.err.println("FAIL" + " " + element.getClass().getSimpleName() + " [" + definition.getClass().getSimpleName() + "]");
              failures.add(element.getClass().getName());
            }
          }
        }
        catch (Throwable ignored) {
        }
      }
    }
    LOG.debug("count: " + classes.size() + ", total: " + total);
    assertEmpty("PsiLanguageInjectionHost requires " + ElementManipulators.EP_NAME, failures);
  }

  public void testInitialRegisterPerformance() {
    PlatformTestUtil.startPerformanceTest("IElementType add", 50, () -> {
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
    }).assertTiming();
  }
}
