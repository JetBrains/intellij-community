// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.codeInsight
import com.intellij.openapi.util.Iconable
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.ui.icons.RowIcon
import com.intellij.util.PsiIconUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.swing.Icon

class FirKotlinIconProviderTest : KotlinLightCodeInsightFixtureTestCase() {
  override fun isFirPlugin(): Boolean {
    return true
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
  }

  override fun tearDown() {
    runAll(
      ThrowableRunnable { project.invalidateCaches() },
      ThrowableRunnable { super.tearDown() }
    )
  }

  fun testJavaBase() {
    val aClass = myFixture.addClass("public class BaseJavaClass {}")
    myFixture.addFileToProject("foo.kt", "class Foo : BaseJavaClass() {}")
    val psiClass = ClassInheritorsSearch.search(aClass).findFirst()!!
    val icon = PsiIconUtil.getProvidersIcon(psiClass, Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS)
    val iconString = (icon.safeAs<RowIcon>()?.allIcons?.joinToString(transform = Icon::toString) ?: icon)?.toString()
    assertEquals("org/jetbrains/kotlin/idea/icons/classKotlin.svg", iconString)
  }
}