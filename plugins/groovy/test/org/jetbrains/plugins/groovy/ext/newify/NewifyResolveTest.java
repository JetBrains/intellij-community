// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.newify;

import com.intellij.psi.PsiReference;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class NewifyResolveTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addClass("""
                            class Aa {
                              String name;
                              public Aa(){}
                            }
                            """);

    myFixture.addClass("""
                            class Cc {
                              String name;
                            }
                            """);
  }

  @Test
  public void testAutoNewify() {
    checkResolve("""
                   
                   @Newify
                   class B {
                     def a = Aa.ne<caret>w()
                   }
                   """, PsiMethod.class);

    checkResolve("""
                   
                   @Newify
                   class B {
                     def a = Aa.ne<caret>w(name :"bar")
                   }
                   """, PsiMethod.class);

    checkResolve("""
                   
                   class B {
                     @Newify
                     def a = Aa.ne<caret>w(name :"bar")
                   }
                   """, PsiMethod.class);

    checkResolve("""
                   
                   class B {
                     @Newify
                     def a (){ return Aa.ne<caret>w(name :"bar")}
                   }
                   """, PsiMethod.class);

    checkResolve("""
                   
                   class B {
                     @Newify(auto = false)
                     def a (){ return Aa.ne<caret>w()}
                   }
                   """);
  }

  @Test
  public void testNewifyByPattern() {
    checkResolve("""
                   
                   @Newify(pattern = /Aa/)
                   class B {
                     def a = A<caret>a()
                   }
                   """, PsiMethod.class, "Aa");

    checkResolve("""
                   
                   @Newify(pattern = /Cc/)
                   class B {
                     def a = C<caret>c()
                   }
                   """, PsiMethod.class, "Cc");

    checkResolve("""
                   
                   @Newify(pattern = /[A-Z].*/)
                   class B {
                     def a = A<caret>a(name :"bar")
                   }
                   """, PsiMethod.class, "Aa");

    checkResolve("""
                   
                   class B {
                     @Newify(pattern = /[A-Z].*/)
                     def a = A<caret>a(name :"bar")
                   }
                   """, PsiMethod.class, "Aa");

    checkResolve("""
                   
                   class B {
                     @Newify(pattern = /[A-Z].*/)
                     def a (){ return A<caret>a(name :"bar")}
                   }
                   """, PsiMethod.class, "Aa");

    checkResolve("""
                   
                   class B {
                     @Newify(pattern = /[a-z].*/)
                     def a (){ return A<caret>a(name :"bar")}
                   }
                   """);

    checkResolve("""
                   
                   @Newify(pattern = /.*/)
                   class B {
                     class zz {
                     }
                    \s
                     def a() { return z<caret>z() }
                   }""", PsiMethod.class, "B.zz");
  }

  @Test
  public void testNewifyByClass() {
    checkResolve("""
                   
                   @Newify(Aa)
                   class B {
                     def a = A<caret>a()
                   }
                   """, PsiMethod.class, "Aa");

    checkResolve("""
                   
                   @Newify(Cc)
                   class B {
                     def a = C<caret>c()
                   }
                   """, PsiMethod.class, "Cc");

    checkResolve("""
                   
                   @Newify(Aa)
                   class B {
                     def a = A<caret>a(name :"bar")
                   }
                   """, PsiMethod.class, "Aa");

    checkResolve("""
                   
                   class B {
                     @Newify(Aa)
                     def a = A<caret>a(name :"bar")
                   }
                   """, PsiMethod.class, "Aa");

    checkResolve("""
                   
                   class B {
                     @Newify(Aa)
                     def a (){ return A<caret>a(name :"bar")}
                   }
                   """, PsiMethod.class, "Aa");

    checkResolve("""
                   
                   class B {
                     @Newify
                     def a (){ return A<caret>a(name :"bar")}
                   }
                   """);
  }

  public void checkResolve(@NotNull String text, @Nullable Class<?> refType, @Nullable String returnType) {
    PsiReference ref = resolve.get().configureByText(text);
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      if (refType != null) {
        assertInstanceOf(refType, resolved);
      }
      else {
        assertNull(resolved);
      }

      if (returnType != null && resolved instanceof PsiMethod method) {
        assertEquals(returnType, method.getReturnType().getCanonicalText());
      }
    });
  }

  public void checkResolve(@NotNull String text, @Nullable Class<?> refType) {
    checkResolve(text, refType, null);
  }

  public void checkResolve(@NotNull String text) {
    checkResolve(text, null, null);
  }
}
