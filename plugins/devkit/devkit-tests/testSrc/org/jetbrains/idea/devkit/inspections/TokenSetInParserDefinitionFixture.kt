// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * Creates a [LightDevKitInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [TokenSetInParserDefinitionInspection]. The project has the stubs of `Language`, `IElementType`, `TokenSet` and `ParserDefinition`.
 */
@JvmOverloads
fun tokenSetInParserDefinitionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("tokenSetInParserDefinitionFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.addClass("""
    package com.intellij.lang;
    public abstract class Language {}
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi.tree;
    
    import com.intellij.lang.Language;
    
    public class IElementType {
      public IElementType(String debugName, Language language) {
        // any
      }
    }
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi;
    
    import com.intellij.psi.tree.IElementType;
    
    public interface TokenType {
      IElementType WHITE_SPACE = null;
    }
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi.tree;
    
    public final class TokenSet {
      public static final TokenSet EMPTY = null;
      public static final TokenSet ANY = null;
      public static final TokenSet WHITE_SPACE = null;
    
      public static TokenSet create(IElementType... types) {
        return null;
      }
    
      public static TokenSet orSet(TokenSet... sets) {
        return null;
      }
    
    }
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.lang;
    
    import com.intellij.psi.tree.TokenSet;
    
    public interface ParserDefinition {
      // only a single TokenSet-related method for simplicity
      TokenSet getCommentTokens();
    }
    """.trimIndent())
  fixture.enableInspections(TokenSetInParserDefinitionInspection())
  initialized(devKit) {}
}
