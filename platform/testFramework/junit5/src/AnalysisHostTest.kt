// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.impl.AnalysisHostTestExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited

/**
 * Runs the tests of a class on the analysis host of the current [AnalysisHostRuntime].
 * An analysis host is the product that runs the code analysis: the IDE application or the language server.
 *
 * Use it on the class in place of [TestApplication]. On the IDE host, it initializes the application as [TestApplication] does.
 *
 * By default, a test runs on each host. [hosts] limits the class or a test method to the listed hosts.
 * On another host, the test reports as disabled.
 * On a method, the annotation can only narrow the hosts of the class, because a disabled class runs no method.
 * A method that lists a host that the class does not list fails.
 * A subclass gets the annotation of its base class, as it gets the extension and the tag.
 *
 * Use [hosts] for a test that depends on one host, for example a test that uses an IDE fixture directly.
 * Write a comment that says why the test needs the host.
 * A helper that a host does not support yet aborts the test itself, so such a test needs no list.
 *
 * Each annotated class and method has the JUnit tag [ANALYSIS_HOST_TAG]. A test target of another host selects the tests by the tag.
 */
@TestOnly
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Inherited
@ExtendWith(AnalysisHostTestExtension::class)
@TestFixtures
@Tag(ANALYSIS_HOST_TAG)
annotation class AnalysisHostTest(
  /**
   * The hosts that run the test.
   */
  val hosts: Array<Host> = [Host.IDE, Host.LSP],
) {
  enum class Host {
    /** The IDE test application. */
    IDE,

    /** The language server, which runs the analyzer in place of the IDE application. */
    LSP,
  }
}

/**
 * The JUnit tag of each [AnalysisHostTest] class and method.
 */
@TestOnly
const val ANALYSIS_HOST_TAG: String = "analysis-host"
