// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.PsiDocumentManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyStubsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testEnumConstant() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.getTempDirFixture().createFile("A.groovy", "enum A { MyEnumConstant }");
    ReadAction.runBlocking(() -> {
      GrEnumConstant ec = (GrEnumConstant)
        PsiShortNamesCache.getInstance(myFixture.getProject()).getFieldsByName("MyEnumConstant", GlobalSearchScope.allScope(myFixture.getProject()))[0];
      PsiFileImpl file = (PsiFileImpl)ec.getContainingFile();
      assertNotNull(file.getStub());
      assertEquals("A", ec.getContainingClass().getQualifiedName());
      assertNotNull(file.getStub());

      assertTrue(Arrays.equals(ec.getContainingClass().getFields(), new PsiField[] {ec}));
      GrTypeDefinitionBody typeDefinitionBody = ((GrTypeDefinition)ec.getContainingClass()).getBody();
      assertTrue(Arrays.equals(((GrEnumDefinitionBody)typeDefinitionBody).getEnumConstantList().getEnumConstants(), new GrEnumConstant[] {ec}));
      assertNotNull(file.getStub());
    });
  }

  @Test
  public void testStubIndexMismatch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ((PsiDocumentManagerEx)PsiDocumentManager.getInstance(myFixture.getProject())).disableBackgroundCommit(myFixture.getTestRootDisposable());

    VirtualFile vFile = myFixture.getTempDirFixture().createFile("foo.groovy");
    final Project project = myFixture.getProject();
    PsiFileImpl fooFile = ReadAction.computeBlocking(() -> (PsiFileImpl)PsiManager.getInstance(project).findFile(vFile));
    final Document fooDocument = ReadAction.computeBlocking(() -> fooFile.getViewProvider().getDocument());
    assertNull(ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(project).findClass("Fooxx", GlobalSearchScope.allScope(project))));
    WriteCommandAction.writeCommandAction(project, fooFile).run(() -> fooDocument.setText("class Fooxx {}"));
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiDocumentManager.getInstance(project).commitDocument(fooDocument);
      fooFile.setTreeElementPointer(null);
      DumbModeTestUtils.runInDumbModeSynchronously(project, () -> {
        assertThat(((GroovyFile)fooFile).getClasses()).hasSize(1);
        if (Registry.is("ide.dumb.mode.check.awareness")) {
          assertFalse(fooFile.isContentsLoaded());
        }
      });
    });
    assertNotNull(ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(project).findClass("Fooxx", GlobalSearchScope.allScope(project))));
  }

  @Test
  public void testErrorInCodeReference() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.getTempDirFixture().createFile("A.groovy", "class A extends foo.B< {}");
    TestUtils.disableAstLoading(myFixture.getProject(), myFixture.getTestRootDisposable());
    ReadAction.runBlocking(() -> {
      PsiClass clazz = myFixture.findClass("A");
      PsiClassType[] extendsTypes = clazz.getExtendsListTypes();
      assertEquals(1, extendsTypes.length);
      PsiClassType type = extendsTypes[0];
      assertTrue(type instanceof GrClassReferenceType);
      GrCodeReferenceElement reference = ((GrClassReferenceType)type).getReference();
      assertEquals("B", reference.getReferenceName());
      assertEquals("foo.B", reference.getQualifiedReferenceName());
    });
  }

  @Test
  public void testUnfinishedTypeArgumentList() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.getTempDirFixture().createFile("A.groovy", "def foo(C<T p)");
    TestUtils.disableAstLoading(myFixture.getProject(), myFixture.getTestRootDisposable());

    ReadAction.runBlocking(() -> {
      GroovyScriptClass clazz = (GroovyScriptClass)myFixture.findClass("A");
      GrMethod method = clazz.getCodeMethods()[0];
      GrParameter parameter = method.getParameterList().getParameters()[0];

      PsiType type = parameter.getType();
      assertTrue(type instanceof GrClassReferenceType);
      assertEquals("C", ((GrClassReferenceType)type).getReference().getReferenceName());

      PsiType typeArgument = ((GrClassReferenceType)type).getParameters()[0];
      assertTrue(typeArgument instanceof GrClassReferenceType);
      assertEquals("T", ((GrClassReferenceType)typeArgument).getReference().getReferenceName());
    });
  }
}