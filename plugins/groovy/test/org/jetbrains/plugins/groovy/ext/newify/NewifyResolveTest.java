// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.newify;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase;
import org.junit.Assert;

public class NewifyResolveTest extends GroovyResolveTestCase {
  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_2_5;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
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
    PsiElement resolved = configureByText(text).resolve();
    if (refType != null) {
      UsefulTestCase.assertInstanceOf(resolved, refType);
    }
    else {
      Assert.assertNull(resolved);
    }

    if (returnType != null && resolved instanceof PsiMethod method) {
      Assert.assertEquals(returnType, method.getReturnType().getCanonicalText());
    }
  }

  public void checkResolve(@NotNull String text, @Nullable Class<?> refType) {
    checkResolve(text, refType, null);
  }

  public void checkResolve(@NotNull String text) {
    checkResolve(text, null, null);
  }
}
