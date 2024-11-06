// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
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

import java.io.IOException;
import java.util.Arrays;

public class GroovyStubsTest extends LightJavaCodeInsightFixtureTestCase {
  public void testEnumConstant() throws IOException {
    myFixture.getTempDirFixture().createFile("A.groovy", "enum A { MyEnumConstant }");
    GrEnumConstant ec = (GrEnumConstant)
      PsiShortNamesCache.getInstance(getProject()).getFieldsByName("MyEnumConstant", GlobalSearchScope.allScope(getProject()))[0];
    PsiFileImpl file = (PsiFileImpl)ec.getContainingFile();
    assertNotNull(file.getStub());
    assertEquals("A", ec.getContainingClass().getQualifiedName());
    assertNotNull(file.getStub());

    assertTrue(Arrays.equals(ec.getContainingClass().getFields(), new PsiField[] {ec}));
    GrTypeDefinitionBody typeDefinitionBody = ((GrTypeDefinition)ec.getContainingClass()).getBody();
    assertTrue(Arrays.equals(((GrEnumDefinitionBody)typeDefinitionBody).getEnumConstantList().getEnumConstants(), new GrEnumConstant[] {ec}));
    assertNotNull(file.getStub());
  }

  public void testStubIndexMismatch() {
    ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(getProject())).disableBackgroundCommit(myFixture.getTestRootDisposable());

    VirtualFile vFile = myFixture.getTempDirFixture().createFile("foo.groovy");
    final Project project = myFixture.getProject();
    PsiFileImpl fooFile = (PsiFileImpl)PsiManager.getInstance(project).findFile(vFile);
    final Document fooDocument = fooFile.getViewProvider().getDocument();
    assertNull(JavaPsiFacade.getInstance(project).findClass("Fooxx", GlobalSearchScope.allScope(project)));
    WriteCommandAction.writeCommandAction(project, fooFile).run(() -> fooDocument.setText("class Fooxx {}"));
    PsiDocumentManager.getInstance(project).commitDocument(fooDocument);
    fooFile.setTreeElementPointer(null);
    DumbModeTestUtils.runInDumbModeSynchronously(project, () -> {
      UsefulTestCase.assertOneElement(((GroovyFile)fooFile).getClasses());
      if (Registry.is("ide.dumb.mode.check.awareness")) {
        TestCase.assertFalse(fooFile.isContentsLoaded());
      }
    });
    assertNotNull(JavaPsiFacade.getInstance(project).findClass("Fooxx", GlobalSearchScope.allScope(project)));
  }

  public void testErrorInCodeReference() throws IOException {
    myFixture.getTempDirFixture().createFile("A.groovy", "class A extends foo.B< {}");
    TestUtils.disableAstLoading(getProject(), getTestRootDisposable());
    PsiClass clazz = myFixture.findClass("A");
    PsiClassType[] extendsTypes = clazz.getExtendsListTypes();
    assertEquals(1, extendsTypes.length);
    PsiClassType type = extendsTypes[0];
    assertTrue(type instanceof GrClassReferenceType);
    GrCodeReferenceElement reference = ((GrClassReferenceType)type).getReference();
    assertEquals("B", reference.getReferenceName());
    assertEquals("foo.B", reference.getQualifiedReferenceName());
  }

  public void testUnfinishedTypeArgumentList() throws IOException {
    myFixture.getTempDirFixture().createFile("A.groovy", "def foo(C<T p)");
    TestUtils.disableAstLoading(getProject(), getTestRootDisposable());

    GroovyScriptClass clazz = (GroovyScriptClass)myFixture.findClass("A");
    GrMethod method = clazz.getCodeMethods()[0];
    GrParameter parameter = method.getParameterList().getParameters()[0];

    PsiType type = parameter.getType();
    assertTrue(type instanceof GrClassReferenceType);
    assertEquals("C", ((GrClassReferenceType)type).getReference().getReferenceName());

    PsiType typeArgument = ((GrClassReferenceType)type).getParameters()[0];
    assertTrue(typeArgument instanceof GrClassReferenceType);
    assertEquals("T", ((GrClassReferenceType)typeArgument).getReference().getReferenceName());
  }
}