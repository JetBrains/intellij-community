// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.TestLibrary;
import org.junit.Assert;
import org.junit.Ignore;

import java.io.IOException;

public class Groovyc25Test extends GroovycTestBase {
  @Override
  protected TestLibrary getGroovyLibrary() {
    return GroovyProjectDescriptors.LIB_GROOVY_2_5;
  }

  @Override
  protected boolean isRebuildExpectedAfterChangeInJavaClassExtendedByGroovy() {
    return true;
  }

  @Override
  protected boolean isRebuildExpectedAfterChangesInGroovyWhichUseJava() {
    return false;
  }

  @Override
  public void test_extend_groovy_classes_with_additional_dependencies() {
    ModuleRootModificationUtil.updateModel(getModule(), model -> {
        MavenDependencyUtil.addFromMaven(model, "org.codehaus.groovy:groovy-test:2.5.23", false);
    });
    super.test_extend_groovy_classes_with_additional_dependencies();
  }

  @Override
  public void test_recompile_one_file_that_triggers_chunk_rebuild_inside() throws IOException {
    do_test_recompile_one_file_that_triggers_chunk_rebuild_inside(false);
  }

  protected boolean isRebuildExpectedWhileCompilingDependentTrait() {
    return true;
  }

  public void test_dependent_trait() throws IOException {
    PsiFile ca = myFixture.addFileToProject("A.groovy", "class A implements B { }");
    myFixture.addFileToProject("B.groovy", "trait B { A aaa }");
    UsefulTestCase.assertEmpty(make());
    touch(ca.getVirtualFile());
    if (isRebuildExpectedWhileCompilingDependentTrait()) {
      assert DefaultGroovyMethods.equals(DefaultGroovyMethods.collect(make(), new Closure<String>(this, this) {
        public String doCall(CompilerMessage it) { return it.getMessage(); }

        public String doCall() {
          return doCall(null);
        }
      }), chunkRebuildMessage("Groovy stub generator"));
    }
    else {
      UsefulTestCase.assertEmpty(make());
    }
  }

  @Ignore("The rebuild was caused by a bug in groovy compiler, which is fixed in 2.5.16")
  public void _test_dependent_class_instanceof() throws IOException {
    PsiFile ca = myFixture.addFileToProject("A.groovy", "class A { def usage(x) { x instanceof B } }");
    myFixture.addFileToProject("B.groovy", "class B { A aaa }");
    UsefulTestCase.assertEmpty(make());
    touch(ca.getVirtualFile());
    Assert.assertEquals(chunkRebuildMessage("Groovy compiler"), DefaultGroovyMethods.collect(make(), new Closure<String>(this, this) {
      public String doCall(CompilerMessage it) { return it.getMessage(); }

      public String doCall() {
        return doCall(null);
      }
    }));
  }

  @Ignore("The rebuild was caused by a bug in groovy compiler, which is fixed in 2.5.16")
  public void _test_dependent_class_exception() throws IOException {
    PsiFile ca = myFixture.addFileToProject("A.groovy", "class A { def usage(x) throws B {} }");
    myFixture.addFileToProject("B.groovy", "class B extends Throwable { A aaa }");
    UsefulTestCase.assertEmpty(make());
    touch(ca.getVirtualFile());
    Assert.assertEquals(chunkRebuildMessage("Groovy compiler"), DefaultGroovyMethods.collect(make(), new Closure<String>(this, this) {
      public String doCall(CompilerMessage it) { return it.getMessage(); }

      public String doCall() {
        return doCall(null);
      }
    }));
  }

  @Ignore("The rebuild was caused by a bug in groovy compiler, which is fixed in 2.5.16")
  public void _test_dependent_class_literal() throws IOException {
    PsiFile ca = myFixture.addFileToProject("A.groovy", "class A { def usage() { B.class } }");
    myFixture.addFileToProject("B.groovy", "@groovy.transform.PackageScope class B { A aaa }");
    UsefulTestCase.assertEmpty(make());
    touch(ca.getVirtualFile());
    Assert.assertEquals(chunkRebuildMessage("Groovy compiler"), DefaultGroovyMethods.collect(make(), new Closure<String>(this, this) {
      public String doCall(CompilerMessage it) { return it.getMessage(); }

      public String doCall() {
        return doCall(null);
      }
    }));
  }

  @Ignore("The rebuild was caused by a bug in groovy compiler, which is fixed in 2.5.16")
  public void _test_dependent_class_array() throws IOException {
    PsiFile ca = myFixture.addFileToProject("A.groovy", "class A { def usage() { new B[0] } }");
    myFixture.addFileToProject("B.groovy", "class B { A aaa }");
    UsefulTestCase.assertEmpty(make());
    touch(ca.getVirtualFile());
    Assert.assertEquals(chunkRebuildMessage("Groovy compiler"), DefaultGroovyMethods.collect(make(), new Closure<String>(this, this) {
      public String doCall(CompilerMessage it) { return it.getMessage(); }

      public String doCall() {
        return doCall(null);
      }
    }));
  }
}
