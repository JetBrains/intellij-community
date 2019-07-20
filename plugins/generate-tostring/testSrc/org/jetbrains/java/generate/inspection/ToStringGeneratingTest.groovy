/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.java.generate.inspection


import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.java.generate.GenerateToStringActionHandlerImpl
import org.jetbrains.java.generate.GenerateToStringContext
import org.jetbrains.java.generate.GenerateToStringWorker
import org.jetbrains.java.generate.config.ConflictResolutionPolicy
import org.jetbrains.java.generate.config.ReplacePolicy
import org.jetbrains.java.generate.template.TemplateResource
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManager
/**
 * Created by Max Medvedev on 07/03/14
 */
class ToStringGeneratingTest extends LightJavaCodeInsightFixtureTestCase {
  void testDuplicateToStringAnInnerClass() throws Exception {
    doTest('''\
public class Foobar  {
    private int foo;
    private int bar;

    @Override <caret>
    public String toString() {
        return "Foobar{" +
                "foo=" + foo +
                ", bar=" + bar +
                '}';
    }

    public static class Nested {
    }
}
''', '''\
public class Foobar  {
    private int foo;
    private int bar;

    @Override
    public String <caret>toString() {
        return "Foobar{" +
                "foo=" + foo +
                ", bar=" + bar +
                '}';
    }

    public static class Nested {
    }
}
''', ReplacePolicy.instance)
  }

 void testProtectedFieldInSuper() throws Exception {
    doTest('''\
class Foobar extends Foo {
    private int bar;
    <caret> 
}
class Foo  {
    protected int foo;
}
''', '''\
class Foobar extends Foo {
    private int bar;

    @Override
    public String toString() {
        return "Foobar{" +
                "foo=" + foo +
                ", bar=" + bar +
                '}';
    }
}
class Foo  {
    protected int foo;
}
''', ReplacePolicy.instance)
  }

 void testPrivateFieldWithGetterInSuper() throws Exception {
   def config = GenerateToStringContext.getConfig()
   config.enableMethods = true
   try {
     doTest('''\
class Foobar extends Foo {
    private int bar;
    <caret> 
}
class Foo  {
    private int foo;
    public int getFoo() {
       return foo;
    }
}
''', '''\
class Foobar extends Foo {
    private int bar;

    @Override
    public String toString() {
        return "Foobar{" +
                "foo=" + getFoo() +
                ", bar=" + bar +
                '}';
    }
}
class Foo  {
    private int foo;
    public int getFoo() {
       return foo;
    }
}
''', ReplacePolicy.instance)
   }
   finally {
     config.enableMethods = false
   }
  }

  void testPrivateFieldWithGetterInSuperSortSuperFirst() throws Exception {
   def config = GenerateToStringContext.getConfig()
   config.enableMethods = true
   config.sortElements = 3
   try {
     doTest('''\
class Foobar extends Foo {
    private int bar;
    <caret> 
}
class Foo  {
    private int foo;
    public int getFoo() {
       return foo;
    }
}
''', '''\
class Foobar extends Foo {
    private int bar;

    @Override
    public String toString() {
        return "Foobar{" +
                "foo=" + getFoo() +
                ", bar=" + bar +
                '}';
    }
}
class Foo  {
    private int foo;
    public int getFoo() {
       return foo;
    }
}
''', ReplacePolicy.instance)
   }
   finally {
     config.enableMethods = false
     config.sortElements = 0
   }
  }

  private void doTest(@NotNull String before,
                      @NotNull String after,
                      @NotNull ConflictResolutionPolicy policy,
                      @NotNull TemplateResource template = findDefaultTemplate()) {
    myFixture.configureByText('a.java', before)

    PsiClass clazz = findClass()
    Collection<PsiMember> members = collectMembers(clazz)
    GenerateToStringWorker worker = buildWorker(clazz, policy)

    WriteCommandAction.runWriteCommandAction(myFixture.project, "","", {
        worker.execute(members, template, policy)
      }, myFixture.file)

    myFixture.checkResult(after)
  }

  @NotNull
  private GenerateToStringWorker buildWorker(@NotNull PsiClass clazz, @NotNull ConflictResolutionPolicy policy) {
    new GenerateToStringWorker(clazz, myFixture.editor, true) {
      @Override
      protected ConflictResolutionPolicy exitsMethodDialog(TemplateResource template) {
        policy
      }
    }
  }

  @NotNull
  private static TemplateResource findDefaultTemplate() {
    Collection<TemplateResource> templates = ToStringTemplatesManager.getInstance().getAllTemplates()
    def template = templates.find { it.fileName == "String concat (+)" }
    assert template != null
    template
  }

  @NotNull
  private static Collection<PsiMember> collectMembers(@NotNull PsiClass clazz) {
    def memberElements = GenerateToStringActionHandlerImpl.buildMembersToShow(clazz)
    memberElements.collect {mem -> (PsiMember) mem.element}. sort { o1, o2 -> compareMembers(o1, o2) }
  }

  private static int compareMembers(PsiMember o1, PsiMember o2) {
    def c1 = o1.getContainingClass()
    def c2 = o2.getContainingClass()
    c1 == c2 ? o2.getName() <=> o1.getName() //descending
             : c1.isInheritor(c2, true) ? 1 : -1
  }

  @NotNull
  private PsiClass findClass() {
    PsiFile file = myFixture.file
    assert file instanceof PsiJavaFile
    PsiClass[] classes = file.classes

    assert classes.length > 0
    classes[0]
  }
}
