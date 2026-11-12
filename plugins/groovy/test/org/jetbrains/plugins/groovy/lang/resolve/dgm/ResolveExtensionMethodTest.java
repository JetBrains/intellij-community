// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.dgm;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.dgm.DGMUtil.ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;

@TestApplication
public class ResolveExtensionMethodTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  private void addExtension(String directory, String text) {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    PsiFileFactory factory = PsiFileFactory.getInstance(myFixture.getProject());
    String fqn = ReadAction.computeBlocking(() -> {
      GroovyFile file =
        (GroovyFile)factory.createFileFromText("a.groovy", GroovyFileType.GROOVY_FILE_TYPE, text);
      return file.getTypeDefinitions()[0].getQualifiedName();
    });
    String path = StringUtil.join(fqn.split("\\."), "/") + ".groovy";
    myFixture.addFileToProject(path, text);
    myFixture.addFileToProject("META-INF/" + directory + "/" + ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE,
                                  """
                                    moduleName=ext-module
                                    moduleVersion=1.0
                                    extensionClasses=""" +
                                  fqn +
                                  """
                                    
                                    """);
  }

  private void addExtension(String text) {
    addExtension("services", text);
  }

  @Test
  public void testPreferExtensionMethodWoConversionsOverClassMethodWithSamConversion() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    @Language("JAVA") String s = """
      package com.foo.baz;
      
      public interface I {
        void sam();
      }
      """;
    myFixture.addClass(s);

    myFixture.addClass(
      """
        package com.foo.bar;
        import com.foo.baz.I;
        
        public interface D {
          void myMethod(I i);
        }
        """);

    addExtension(
      """
        package com.foo.bad
        import com.foo.bar.D
        
        class Exts {
          static void myMethod(D d, Closure c) {}
        }
        """);
    DumbService.getInstance(myFixture.getProject()).waitForSmartMode();
    resolve.get().resolveByText(
      """
        import com.foo.bar.D
        
        void foo(D d) {
          d.my<caret>Method {}
        }
        """, GrGdkMethod.class);
  }

  @Test
  public void testResolveExtensionMethod() {
    addExtension(
      """
        class StringExtensions {
          static void myMethod(String s) {}
        }
        """);
    resolve.get().resolveByText(
      """
        "hi".<caret>myMethod()
        """, GrGdkMethod.class);
  }

  @Test
  public void testResolveExtensionMethodFromGroovyDirectory() {
    addExtension("groovy",
                 """
                   class StringExtensions {
                     static void myMethod(String s) {}
                   }
                   """);
    resolve.get().resolveByText(
      """
        "hi".<caret>myMethod()
        """, GrGdkMethod.class);
  }
}
