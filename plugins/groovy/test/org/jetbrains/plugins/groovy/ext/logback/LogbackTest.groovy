/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.ext.logback

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.pom.PomTargetPsiElement
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

import static org.jetbrains.plugins.groovy.config.GroovyFacetUtil.getBundledGroovyJar
import static org.jetbrains.plugins.groovy.util.TestUtils.getAbsoluteTestDataPath

@CompileStatic
class LogbackTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = new GroovyLightProjectDescriptor(bundledGroovyJar as String) {

    @NotNull
    final JpsModuleSourceRootType sourceRootType = JavaResourceRootType.RESOURCE

    @Override
    void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry)
      def library = model.moduleLibraryTable.createLibrary("Logback")
      library.modifiableModel.with {
        def root = JarFileSystem.instance.refreshAndFindFileByPath(absoluteTestDataPath + "mock/logback/logback-mock.jar!/")
        addRoot(root, OrderRootType.CLASSES)
        commit()
      }
      model.findLibraryOrderEntry(library).scope = DependencyScope.RUNTIME
    }
  }

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
    <warning descr="Cannot resolve symbol 'setEncoder'">setEncoder</warning>(new Encoder(){})
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
