// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.logback

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.roots.DependencyScope
import com.intellij.pom.PomTargetPsiElement
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.RepositoryTestLibrary
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

import static org.jetbrains.plugins.groovy.GroovyProjectDescriptors.LIB_GROOVY_LATEST

@CompileStatic
class LogbackTest extends LightGroovyTestCase {

  private static final RepositoryTestLibrary LIB_LOGBACK = new RepositoryTestLibrary(
    "ch.qos.logback:logback-classic:1.2.3",
    DependencyScope.RUNTIME
  )
  private static final LightProjectDescriptor LOGBACK = new LibraryLightProjectDescriptor(LIB_GROOVY_LATEST + LIB_LOGBACK) {
    @NotNull
    final JpsModuleSourceRootType sourceRootType = JavaResourceRootType.RESOURCE
  }

  final LightProjectDescriptor projectDescriptor = LOGBACK

  void 'test highlighting'() {
    fixture.with {
      configureByText 'logback.groovy', '''\
appender("FULL_STACKTRACE", FileAppender) {
    file = "./stacktrace.log"
    append = true
    <warning descr="Cannot resolve symbol 'setAppend'">setAppend</warning>(true)
    encoder(PatternLayoutEncoder) {
        pattern = "%level %logger - %msg%n"
    }
    <warning descr="Cannot resolve symbol 'encoder'">encoder</warning>
    <warning descr="Cannot resolve symbol 'setEncoder'">setEncoder</warning>(new EchoEncoder())
}

logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
root(warn, [''])
'''
      enableInspections(GrUnresolvedAccessInspection)
      checkHighlighting()
    }
  }

  void 'test component delegate completion'() {
    fixture.with {
      configureByText 'logback.groovy', '''\
appender("FULL_STACKTRACE", FileAppender) {
    <caret>
}

logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
'''
      completeBasic()
      def lookupStrings = lookupElementStrings.toSet()
      ['file', 'append', 'encoder'].each {
        assert it in lookupStrings
      }
    }
  }

  void 'test target completion'() {
    fixture.configureByText 'logback.groovy', '''\
appender('FOO_APP', ConsoleAppender)
appender("BAR_APP", FileAppender) {}
logger("", ERROR, ['<caret>'])
'''
    fixture.completeBasic()
    assert ['FOO_APP', 'BAR_APP'].toSet() == fixture.lookupElementStrings.toSet()
  }

  void 'test target navigation'() {
    fixture.with {
      configureByText 'logback.groovy', '''\
appender('FOO_APP', ConsoleAppender)
appender("BAR_APP", FileAppender) {}
logger("", ERROR, ['FOO<caret>_APP'])
'''
      performEditorAction 'GotoDeclaration'
      checkResult '''\
appender('<caret>FOO_APP', ConsoleAppender)
appender("BAR_APP", FileAppender) {}
logger("", ERROR, ['FOO_APP'])
'''
    }
  }

  void 'test element to find usages of exists'() {
    fixture.with {
      configureByText 'logback.groovy', '''\
appender('FOO_<caret>APP', ConsoleAppender)
'''
      def targetElement = GotoDeclarationAction.findElementToShowUsagesOf(editor, caretOffset) as PomTargetPsiElement
      assert targetElement
      assert targetElement.target instanceof AppenderTarget
    }
  }

  void 'test no error when class with same name exists'() {
    fixture.with {
      addClass '''\
package pckg1;
public class SomeClass {}
'''
      addClass '''
package pckg2;
public class SomeConfigurableClass {
  public void setSomeClass(pckg1.SomeClass someClass) {}
}'''
      configureByText 'logback.groovy', '''\
appender('foo', pckg2.SomeConfigurableClass) {
  someClass = SomeClass.<caret>
}
'''
      enableInspections GrUnresolvedAccessInspection
      completeBasic()
    }
  }
}
