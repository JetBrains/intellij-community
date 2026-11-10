// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.indexing.FileBasedIndex;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestDataPath("$CONTENT_ROOT/testData/dom/index/extensionImplementation")
@TestApplication
public class ExtensionImplementationIndexTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "dom/index/extensionImplementation");

  @Test
  public void testKeysAndOffsets() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.copyFileToProject("plugin.xml", "META-INF/plugin.xml");
    String text = getText(file);
    Map<String, IntList> data = ReadAction.computeBlocking(() -> FileBasedIndex.getInstance().getFileData(ExtensionImplementationIndex.NAME, file, myFixture.getProject()));

    int myEp1 = text.indexOf("<myEp implementation=\"myPkg.MyImplementation\" os");
    int myEp2 = text.indexOf("<myEp implementation=\"myPkg.Outer$Inner$Deepest\"");
    int myEp3 = text.indexOf("<myEp implementation=\"myPkg.MyImplementation\"/>");
    int service = text.indexOf("<applicationService");
    int contributor = text.indexOf("<completion.contributor");
    int bean = text.indexOf("<myBeanEp");
    int dotted = text.indexOf("<dottedEp");

    assertThat(data.keySet()).containsOnly("myPkg.MyImplementation", "myPkg.MyService", "myPkg.MyServiceImpl", "myPkg.MyContributor", "myPkg.MyBeanClass", "myPkg.Deep$Nested", "myPkg.Deep", "myPkg.Outer$Inner$Deepest", "myPkg.Outer$Inner", "myPkg.Outer", "myPkg.Outer.DottedInner", "ep:myEp", "ep:applicationService", "ep:contributor", "ep:myBeanEp", "ep:fold", "ep:dottedEp", "ep:ext");

    assertOffsets(data, "myPkg.MyImplementation", myEp1, myEp3);
    assertOffsets(data, "myPkg.MyService", service);
    assertOffsets(data, "myPkg.MyServiceImpl", service);
    assertOffsets(data, "myPkg.MyContributor", contributor);
    assertOffsets(data, "myPkg.MyBeanClass", bean);
    assertOffsets(data, "myPkg.Deep$Nested", bean);
    assertOffsets(data, "myPkg.Deep", bean);
    assertOffsets(data, "myPkg.Outer$Inner$Deepest", myEp2);
    assertOffsets(data, "myPkg.Outer$Inner", myEp2);
    assertOffsets(data, "myPkg.Outer", myEp2);
    assertOffsets(data, "myPkg.Outer.DottedInner", dotted);
    assertOffsets(data, "ep:myEp", myEp1, myEp2, myEp3);
    assertOffsets(data, "ep:contributor", contributor);
  }

  @Test
  public void testExtensionPointShortNames() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var file = myFixture.addFileToProject("META-INF/plugin.xml", """
      <idea-plugin>
        <extensions defaultExtensionNs="com.intellij">
          <contributor/>
          <completion.contributor/>
          <other id="contributor"><contributor/></other>
          <contributorExtra/>
        </extensions>
      </idea-plugin>
      """);
    myFixture.addFileToProject("other.xml", """
      <idea-plugin><extensions defaultExtensionNs="other"><contributor/></extensions></idea-plugin>
      """);
    var scope = GlobalSearchScope.fileScope(file);
    var names = new ArrayList<String>();
    ReadAction.runBlocking(() -> {
      assertTrue(ExtensionImplementationIndex.processExtensionsByEpShortName(myFixture.getProject(), "contributor", scope, tag -> {
        names.add(tag.getName());
        return true;
      }));
      assertIterableEquals(List.of("contributor", "completion.contributor"), names);

      names.clear();
      assertFalse(ExtensionImplementationIndex.processExtensionsByEpShortName(myFixture.getProject(), "contributor", scope, tag -> {
        names.add(tag.getName());
        return false;
      }));
      assertThat(names).hasSize(1);
    });
  }

  @Test
  public void testExtensionPointShortNamesAcrossFiles() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int fileCount = 24;
    var expectedIds = List.of("first", "second", "third", "fourth", "fifth", "sixth",
                             "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth");
    for (int i = 0; i < fileCount; i++) {
      var xml = new StringBuilder("<idea-plugin><extensions defaultExtensionNs=\"com.intellij\">");
      for (var id : expectedIds) {
        xml.append("<contributor id=\"").append(id).append("\"/>");
      }
      xml.append("</extensions></idea-plugin>");
      myFixture.addFileToProject("plugin" + i + ".xml", xml.toString());
    }
    var scope = GlobalSearchScope.projectScope(myFixture.getProject());
    var idsByFile = new ConcurrentHashMap<String, List<String>>();
    ReadAction.runBlocking(() -> {
      assertTrue(ExtensionImplementationIndex.processExtensionsByEpShortName(myFixture.getProject(), "contributor", scope, tag -> {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        idsByFile.computeIfAbsent(tag.getContainingFile().getName(), _ -> new ArrayList<>()).add(tag.getAttributeValue("id"));
        return true;
      }));
      assertThat(idsByFile.keySet()).hasSize(fileCount);
      for (var ids : idsByFile.values()) {
        assertIterableEquals(expectedIds, ids);
      }

      var processed = new AtomicInteger();
      assertFalse(ExtensionImplementationIndex.processExtensionsByEpShortName(myFixture.getProject(), "contributor", scope, _ -> {
        processed.incrementAndGet();
        return false;
      }));
      assertTrue(processed.get() > 0 && processed.get() <= fileCount);
    });
  }

  @Test
  public void testExtensionPointLookupCancellation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var file = myFixture.copyFileToProject("plugin.xml", "META-INF/plugin.xml");
    var scope = GlobalSearchScope.fileScope(myFixture.getProject(), file);
    var indicator = new EmptyProgressIndicator();
    var processed = new AtomicInteger();
    ReadAction.runBlocking(() -> {
      assertThrows(ProcessCanceledException.class, () -> ProgressManager.getInstance().runProcess(() ->
        ExtensionImplementationIndex.processExtensionsByEpShortName(myFixture.getProject(), "myEp", scope, _ -> {
          processed.incrementAndGet();
          indicator.cancel();
          return true;
        }), indicator));
    });
    assertEquals(1, processed.get());
  }

  @Test
  public void testProcessExtensions() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.copyFileToProject("plugin.xml", "META-INF/plugin.xml");
    String text = getText(file);
    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());

    List<XmlTag> tags = new ArrayList<>();
    ReadAction.runBlocking(() -> {
      assertTrue(ExtensionImplementationIndex.processExtensions(myFixture.getProject(), "myPkg.MyImplementation", scope, tags::add));
      assertThat(tags).hasSize(2);
      for (XmlTag tag : tags) {
        assertEquals("myEp", tag.getName());
        assertEquals("myPkg.MyImplementation", tag.getAttributeValue("implementation"));
      }

      tags.clear();
      assertTrue(ExtensionImplementationIndex.processExtensions(myFixture.getProject(), "myPkg.Outer", scope, tags::add));
      XmlTag outer = assertThat(tags).singleElement().actual();
      assertEquals(text.indexOf("<myEp implementation=\"myPkg.Outer$Inner$Deepest\""), outer.getTextOffset());
      assertEquals("myPkg.Outer$Inner$Deepest", outer.getAttributeValue("implementation"));

      tags.clear();
      assertTrue(ExtensionImplementationIndex.processExtensions(myFixture.getProject(), "myPkg.Deep", scope, tags::add));
      assertEquals("myBeanEp", assertThat(tags).singleElement().actual().getName());

      tags.clear();
      assertFalse(ExtensionImplementationIndex.processExtensions(myFixture.getProject(), "myPkg.MyImplementation", scope, tag -> {
        tags.add(tag);
        return false;
      }));
      assertThat(tags).hasSize(1);

      assertTrue(ExtensionImplementationIndex.processExtensions(myFixture.getProject(), "myPkg.MyClass.foo", scope, ExtensionImplementationIndexTest::unexpected));
      assertTrue(ExtensionImplementationIndex.processExtensions(myFixture.getProject(), "myPkg.MyClass", scope, ExtensionImplementationIndexTest::unexpected));
    });
  }

  @Test
  public void testIsClassLike() {
    assertTrue(ExtensionImplementationIndex.isClassLike("myPkg.MyClass"));
    assertTrue(ExtensionImplementationIndex.isClassLike("a.b.C"));
    assertTrue(ExtensionImplementationIndex.isClassLike("a.B.c"));
    assertTrue(ExtensionImplementationIndex.isClassLike("com.foo.Outer$Inner"));
    assertTrue(ExtensionImplementationIndex.isClassLike("com.foo_bar.Baz2"));
    assertTrue(ExtensionImplementationIndex.isClassLike("_a.B$"));

    assertFalse(ExtensionImplementationIndex.isClassLike(""));
    assertFalse(ExtensionImplementationIndex.isClassLike("MyClass"));
    assertFalse(ExtensionImplementationIndex.isClassLike("_a.$B"));
    assertFalse(ExtensionImplementationIndex.isClassLike("com.intellij"));
    assertFalse(ExtensionImplementationIndex.isClassLike("lowercase.only.value"));
    assertFalse(ExtensionImplementationIndex.isClassLike("at myPkg.MyClass.foo("));
    assertFalse(ExtensionImplementationIndex.isClassLike("myPkg.MyClass."));
    assertFalse(ExtensionImplementationIndex.isClassLike(".myPkg.MyClass"));
    assertFalse(ExtensionImplementationIndex.isClassLike("myPkg..MyClass"));
    assertFalse(ExtensionImplementationIndex.isClassLike("1.0"));
    assertFalse(ExtensionImplementationIndex.isClassLike("my.Plugin-Id"));
    assertFalse(ExtensionImplementationIndex.isClassLike("META-INF/Plugin.xml"));
  }

  @Test
  public void testExactAndPrefixMatches() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var file = myFixture.addFileToProject("META-INF/plugin.xml", """
      <idea-plugin>
        <extensions defaultExtensionNs="com.intellij">
          <myEp id="prefix" implementation="myPkg.Outer$Inner"/>
          <myEp id="prefixFirst" implementation="myPkg.Outer$Inner" other="myPkg.Outer"/>
          <myEp id="exactFirst" implementation="myPkg.Outer" other="myPkg.Outer$Inner"/>
          <myEp id="nested" implementation="myPkg.Outer$Inner"><className> myPkg.Outer </className></myEp>
        </extensions>
      </idea-plugin>
      """);
    var scope = GlobalSearchScope.fileScope(file);
    var ids = new ArrayList<String>();
    ReadAction.runBlocking(() -> {
      assertTrue(ExtensionImplementationIndex.processExtensions(myFixture.getProject(), "myPkg.Outer", scope, true, tag -> {
        ids.add(tag.getAttributeValue("id"));
        return true;
      }));
      assertIterableEquals(List.of("prefixFirst", "exactFirst", "nested"), ids);

      ids.clear();
      assertTrue(ExtensionImplementationIndex.processExtensions(myFixture.getProject(), "myPkg.Outer", scope, tag -> {
        ids.add(tag.getAttributeValue("id"));
        return true;
      }));
      assertIterableEquals(List.of("prefix", "prefixFirst", "exactFirst", "nested"), ids);
    });
  }

  @Test
  public void testXIncludesAreSkipped() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var file = myFixture.addFileToProject("META-INF/plugin.xml", """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <extensions defaultExtensionNs="com.intellij">
          <xi:include href="top.xml"/>
          <myEp implementation="myPkg.MyImplementation"><xi:include href="nested.xml"/></myEp>
        </extensions>
      </idea-plugin>
      """).getVirtualFile();
    Map<String, IntList> data = ReadAction.computeBlocking(() -> FileBasedIndex.getInstance().getFileData(ExtensionImplementationIndex.NAME, file, myFixture.getProject()));

    assertThat(data.keySet()).containsOnly("myPkg.MyImplementation", "ep:myEp");
  }

  @Test
  public void testStrictMatchSkipsOuterPrefixes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var file = myFixture.copyFileToProject("plugin.xml", "META-INF/plugin.xml");
    var scope = GlobalSearchScope.fileScope(myFixture.getProject(), file);
    ReadAction.runBlocking(() -> {
      assertTrue(ExtensionImplementationIndex.processExtensions(myFixture.getProject(), "myPkg.Outer", scope, true,
                                                               ExtensionImplementationIndexTest::unexpected));
      var tags = new ArrayList<XmlTag>();
      assertTrue(ExtensionImplementationIndex.processExtensions(myFixture.getProject(), "myPkg.Outer$Inner$Deepest", scope, true, tags::add));
      assertThat(tags).hasSize(1);
    });
  }

  @Test
  public void testValueExternalizer() throws IOException {
    var externalizer = new ExtensionImplementationIndex().getValueExternalizer();
    for (var values : List.of(IntList.of(), IntList.of(40000), IntList.of(40001), IntList.of(0, 384, 40001))) {
      var bytes = new ByteArrayOutputStream();
      externalizer.save(new DataOutputStream(bytes), values);
      assertEquals(values, externalizer.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
      if (values.size() == 1) assertTrue(bytes.size() < 5);
    }
  }

  private static boolean unexpected(XmlTag tag) {
    fail("unexpected " + tag.getName());
    return true;
  }

  private String getText(VirtualFile file) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile psiFile = ReadAction.computeBlocking(() -> PsiManager.getInstance(myFixture.getProject()).findFile(file));
    assertNotNull(psiFile);
    return ReadAction.computeBlocking(() -> psiFile.getText());
  }

  private static void assertOffsets(Map<String, IntList> data, String key, int... expected) {
    IntList actual = data.get(key);
    assertNotNull(actual, "no key " + key);
    List<Integer> expectedList = new ArrayList<>();
    for (int offset : expected) {
      assertTrue(offset >= 0, "fixture marker not found for " + key);
      expectedList.add(offset);
    }
    IntList offsets = new IntArrayList(actual.size());
    for (int i = 0; i < actual.size(); i++) {
      offsets.add(actual.getInt(i) >>> 1);
    }
    assertIterableEquals(expectedList, offsets, key);
  }
}
