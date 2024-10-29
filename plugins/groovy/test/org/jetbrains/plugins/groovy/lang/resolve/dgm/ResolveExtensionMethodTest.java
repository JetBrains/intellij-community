// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.dgm;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.LightProjectDescriptor;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase;

import static org.jetbrains.plugins.groovy.dgm.DGMUtil.ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE;

public class ResolveExtensionMethodTest extends GroovyResolveTestCase {

  @NotNull
  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private void addExtension(String directory, String text) {
    PsiFileFactory factory = PsiFileFactory.getInstance(getProject());
    GroovyFile file =
      (GroovyFile)factory.createFileFromText("a.groovy", GroovyFileType.GROOVY_FILE_TYPE, text);
    String fqn = file.getTypeDefinitions()[0].getQualifiedName();
    String path = StringUtil.join(fqn.split("\\."), "/") + ".groovy";
    getFixture().addFileToProject(path, text);
    getFixture().addFileToProject("META-INF/" + directory + "/" + ORG_CODEHAUS_GROOVY_RUNTIME_EXTENSION_MODULE,
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

  public void testPreferExtensionMethodWoConversionsOverClassMethodWithSamConversion() {
    @Language("JAVA") String s = """
      package com.foo.baz;
      
      public interface I {
        void sam();
      }
      """;
    getFixture().addClass(s);

    getFixture().addClass(
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
    resolveByText(
      """
        import com.foo.bar.D
        
        void foo(D d) {
          d.my<caret>Method {}
        }
        """, GrGdkMethod.class);
  }

  public void testResolveExtensionMethod() {
    addExtension(
      """
        class StringExtensions {
          static void myMethod(String s) {}
        }
        """);
    resolveByText(
      """
        "hi".<caret>myMethod()
        """, GrGdkMethod.class);
  }

  public void testResolveExtensionMethodFromGroovyDirectory() {
    addExtension("groovy",
                 """
                   class StringExtensions {
                     static void myMethod(String s) {}
                   }
                   """);
    resolveByText(
      """
        "hi".<caret>myMethod()
        """, GrGdkMethod.class);
  }
}
