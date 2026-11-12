// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.singleton;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class SingletonTransformationSupportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFileImpl file = (GroovyFileImpl) myFixture.addFileToProject("singletons.groovy", """
        @Singleton
        class Simple {}
        
        @Singleton(property = "coolInstance")
        class CustomName {}
        
        @Singleton(strict = false)
        class DefaultConstructor {
            DefaultConstructor() {}
        }
        
        @Singleton(strict = false)
        class CustomConstructor {
            CustomConstructor(int a, String b) {}
        }
        
        @Singleton(lazy = true)
        class Lazy {}
        
        @Singleton(strict = false, lazy = true)
        class LazyDefaultConstructor {
            LazyDefaultConstructor() {}
        }
        """);

    myFixture.configureByText("_.groovy", """
        Simple.<warning descr="Cannot resolve symbol 'setInstance'">setInstance</warning>(null)
        Simple simple
        simple = Simple.getInstance()
        simple = Simple.instance
        simple = new Simple()
        
        CustomName.<warning descr="Cannot resolve symbol 'setCoolInstance'">setCoolInstance</warning>(null)
        CustomName customName
        customName = CustomName.getCoolInstance()
        customName = CustomName.coolInstance
        customName = new CustomName()
        
        DefaultConstructor.<warning descr="Cannot resolve symbol 'setInstance'">setInstance</warning>(null)
        DefaultConstructor defaultConstructor
        defaultConstructor = DefaultConstructor.getInstance()
        defaultConstructor = DefaultConstructor.instance
        defaultConstructor = new DefaultConstructor()
        
        CustomConstructor customConstructor
        customConstructor = CustomConstructor.getInstance()
        customConstructor = CustomConstructor.instance
        customConstructor = new CustomConstructor()
        customConstructor = new CustomConstructor(1, "")
        
        Lazy lazy
        lazy = Lazy.getInstance()
        lazy = Lazy.instance
        lazy = new Lazy()
        
        LazyDefaultConstructor lazyDefaultConstructor
        lazyDefaultConstructor = LazyDefaultConstructor.getInstance()
        lazyDefaultConstructor = LazyDefaultConstructor.instance
        lazyDefaultConstructor = new LazyDefaultConstructor()
        """);
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.checkHighlighting();

    myFixture.configureByText("Main.java", """
        public class Main {
          public static void main(String[] args) {
            Simple simple;
            simple = Simple.getInstance();
            simple = Simple.instance;
            simple = new <error descr="'Simple()' has private access in 'Simple'">Simple</error>();
        
            CustomName customName;
            customName = CustomName.getCoolInstance();
            customName = CustomName.coolInstance;
            customName = new <error descr="'CustomName()' has private access in 'CustomName'">CustomName</error>();
        
            DefaultConstructor defaultConstructor;
            defaultConstructor = DefaultConstructor.getInstance();
            defaultConstructor = DefaultConstructor.instance;
            defaultConstructor = new DefaultConstructor();
        
            CustomConstructor customConstructor;
            customConstructor = CustomConstructor.getInstance();
            customConstructor = CustomConstructor.instance;
            customConstructor = new <error descr="'CustomConstructor()' has private access in 'CustomConstructor'">CustomConstructor</error>();
            customConstructor = new CustomConstructor(1, "");
        
            Lazy lazy;
            lazy = Lazy.getInstance();
            lazy = Lazy.<error descr="'instance' has private access in 'Lazy'">instance</error>;
            lazy = new <error descr="'Lazy()' has private access in 'Lazy'">Lazy</error>();
        
            LazyDefaultConstructor lazyDefaultConstructor;
            lazyDefaultConstructor = LazyDefaultConstructor.getInstance();
            lazyDefaultConstructor = LazyDefaultConstructor.<error descr="'instance' has private access in 'LazyDefaultConstructor'">instance</error>;
            lazyDefaultConstructor = new LazyDefaultConstructor();
          }
        }
        """);
    myFixture.checkHighlighting();
    assertFalse(file.isContentsLoaded());
  }
}
