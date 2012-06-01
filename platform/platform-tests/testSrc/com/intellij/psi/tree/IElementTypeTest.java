/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.tree;

import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author gregsh
 */
public class IElementTypeTest extends LightPlatformCodeInsightFixtureTestCase {

  // load all parser definitions, instantiate all lexers & parsers to initialize all IElementType constants
  @SuppressWarnings("UnusedDeclaration")
  public void testCount() throws Exception {
    int count = IElementType.getAllocatedTypesCount();
    System.out.println("Before: " + count +" types");
    LanguageExtensionPoint[] extensions = Extensions.getExtensions(new ExtensionPointName<LanguageExtensionPoint>("com.intellij.lang.parserDefinition"));
    System.out.println(extensions.length +" definitions");

    for (LanguageExtensionPoint e : extensions) {
      ParserDefinition definition = (ParserDefinition)e.getInstance();
      try {
        IFileElementType type = definition.getFileNodeType();
        Lexer lexer = definition.createLexer(getProject());
        PsiParser parser = definition.createParser(getProject());
      }
      catch (UnsupportedOperationException e1) {
      }
    }
    System.out.println("After: " + IElementType.getAllocatedTypesCount() +" types");
    // leave some index-space for plugin developers
    assertTrue(IElementType.getAllocatedTypesCount() < 10000);

    // output on 11.05.2012
    //   Before: 3485 types
    //   95 definitions
    //   After: 7694 types
  }

}
