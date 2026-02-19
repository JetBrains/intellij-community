package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.PsiClassType
import com.intellij.util.asSafely
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getDelegatesToInfo
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest

/**
 * @see GradleDelegatesToProvider
 */
class GradleClosureResolveTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `delegate is not resolving if method has just Closure`(gradleVersion: GradleVersion) {
    val buildScript = """
      |class TestClass {
      |    void method(Closure block) {};
      |}
      |new TestClass().method { <caret> }
      |""".trimMargin()
    testEmptyProject(gradleVersion) {
      testBuildscript(buildScript) {
        val closure = elementUnderCaret(GrClosableBlock::class.java)
        val delegatesToInfo = getDelegatesToInfo(closure)
        assertNull(delegatesToInfo, "It's not expected to resolve a delegate for this Closure")
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `delegate resolves for a Closure with @DelegatesTo`(gradleVersion: GradleVersion) {
    val buildScript = """
      |interface ClassForDelegation
      |class TestClass {
      |    void method(@DelegatesTo(ClassForDelegation) Closure block) {};
      |}
      |new TestClass().method { <caret> }
      |""".trimMargin()
    testEmptyProject(gradleVersion) {
      testBuildscript(buildScript) {
        closureDelegateTest("ClassForDelegation", 0)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `delegate resolves for Action`(gradleVersion: GradleVersion) {
    val buildScript = """
      |class ClassWithGeneric<T> {
      |    void method(Action<T> block) {};
      |}
      |interface ClassForDelegation {}
      |class TestClass extends ClassWithGeneric<ClassForDelegation> {}
      |
      |new TestClass().method { <caret> }
      |""".trimMargin()
    testEmptyProject(gradleVersion) {
      testBuildscript(buildScript) {
        closureDelegateTest("ClassForDelegation", 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `delegate resolves for Action with super type`(gradleVersion: GradleVersion) {
    val buildScript = """
      |class ClassWithGeneric<T> {
      |    void method(Action<? super T> block) {};
      |}
      |interface ClassForDelegation {}
      |class TestClass extends ClassWithGeneric<ClassForDelegation> {}
      |
      |new TestClass().method { <caret> }
      |""".trimMargin()
    testEmptyProject(gradleVersion) {
      testBuildscript(buildScript) {
        closureDelegateTest("ClassForDelegation", 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `when configurations#all(Closure) is called, take a delegate from overload with Action`(gradleVersion: GradleVersion) {
    val buildScript = """
      |configurations.all {
      |  <caret>resolutionStrategy { }
      |}
      |""".trimMargin()
    testEmptyProject(gradleVersion) {
      testBuildscript(buildScript) {
        assertCalledMethodHasClosureParameter()
        closureDelegateTest(GradleCommonClassNames.GRADLE_API_CONFIGURATION, 1)
      }
    }
  }

  /**
   * Similar to the test case for configurations#all, but for a custom class.
   */
  @ParameterizedTest
  @AllGradleVersionsSource
  fun `take delegate from overload with Action if method with Closure was called`(gradleVersion: GradleVersion) {
    val buildScript = """
      |class ClassWithGeneric<T> {
      |    void overloadedWithAction(Closure block) {};
      |    void overloadedWithAction(Action<? super T> block) {};
      |}
      |interface ClassForDelegation {}
      |class TestClass extends ClassWithGeneric<ClassForDelegation> {}
      |
      |new TestClass().overloadedWithAction { <caret> }
      |""".trimMargin()
    testEmptyProject(gradleVersion) {
      testBuildscript(buildScript) {
        assertCalledMethodHasClosureParameter()
        closureDelegateTest("ClassForDelegation", 1)
      }
    }
  }

  // Despite the presence of an overloaded method with Action<? super T>, it's not possible to resolve T to use it as a delegate
  @ParameterizedTest
  @AllGradleVersionsSource
  fun `return null when method type parameter is not resolvable`(gradleVersion: GradleVersion) {
    val buildScript = """
      |class FooClass {
      |    void method(Closure block) {};
      |    <T> void method(Action<? super T> block) {};
      |}
      |new FooClass().method { <caret> }
      |""".trimMargin()
    testEmptyProject(gradleVersion) {
      testBuildscript(buildScript) {
        assertDelegateNotFound()
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `return null when class type parameter is not resolvable`(gradleVersion: GradleVersion) {
    val buildScript = """
      |class FooClass <T> {
      |    void method(Closure block) {};
      |    void method(Action<? super T> block) {};
      |}
      |new FooClass().method { <caret> }
      |""".trimMargin()
    testEmptyProject(gradleVersion) {
      testBuildscript(buildScript) {
        assertDelegateNotFound()
      }
    }
  }

  private fun assertDelegateNotFound() {
    val closableBlock = elementUnderCaret(GrClosableBlock::class.java)
    assertCalledMethodHasClosureParameter()
    val delegatesToInfo = getDelegatesToInfo(closableBlock)
    assertNull(delegatesToInfo, "It's not expected to resolve a delegate for this Closure")
  }

  private fun assertCalledMethodHasClosureParameter() {
    val closableBlock = elementUnderCaret(GrClosableBlock::class.java)
    val methodCall = closableBlock.parent as GrMethodCall
    val lastParameter = methodCall.resolveMethod()?.parameters?.lastOrNull()
    assertNotNull(lastParameter)
    val type = lastParameter?.type?.asSafely<PsiClassType>()
    assertNotNull(type)
    assertTrue(type!!.equalsToText(GROOVY_LANG_CLOSURE))
  }
}