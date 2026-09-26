// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.kotlin.KotlinTester
import com.intellij.util.PathUtil
import org.jetbrains.uast.UElement

abstract class UastHintedVisitorAdapterHintsInspectionTestBase : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
        package com.intellij.lang;
        public abstract class Language {
          public static final Language ANY = null;
        }
      """.trimIndent())
    myFixture.addClass("""
        package com.intellij.psi;
        public abstract class PsiElementVisitor {}
      """.trimIndent())
    myFixture.addClass("""
        package com.intellij.codeInspection;
        public class ProblemsHolder {}
      """.trimIndent())
    myFixture.addClass("""
        package com.intellij.codeInspection;
        import com.intellij.psi.PsiElementVisitor;
        public abstract class LocalInspectionTool {
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) { return null; }
        }
      """.trimIndent())
    myFixture.addClass("""
        package com.intellij.psi;
        import java.util.List;
        public interface HintedPsiElementVisitor {
          List<Class<?>> getHintPsiElements();
        }
      """.trimIndent()
    )
    myFixture.addClass("""
        package com.intellij.uast;
        import com.intellij.lang.Language;
        import com.intellij.psi.HintedPsiElementVisitor;
        import com.intellij.psi.PsiElementVisitor;
        import org.jetbrains.uast.UElement;
        import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;
        
        public class UastHintedVisitorAdapter extends PsiElementVisitor implements HintedPsiElementVisitor {
            public static PsiElementVisitor create(Language language, AbstractUastNonRecursiveVisitor visitor, Class<? extends UElement>[] uElementTypesHint, Boolean directOnly) { return null; }
            public static PsiElementVisitor create(Language language, AbstractUastNonRecursiveVisitor visitor, Class<? extends UElement>[] uElementTypesHint) { return null; }
        }
      """.trimIndent())
    ModuleRootModificationUtil.updateModel(module) {
      KotlinTester.configureKotlinStdLib(it)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    myFixture.enableInspections(UastHintedVisitorAdapterHintsInspection())
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("platform.uast", PathUtil.getJarPathForClass(UElement::class.java))
  }

}
