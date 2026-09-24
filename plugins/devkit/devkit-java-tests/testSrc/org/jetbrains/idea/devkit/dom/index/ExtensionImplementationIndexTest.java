// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.indexing.FileBasedIndex;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

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

@TestDataPath("$CONTENT_ROOT/testData/dom/index/extensionImplementation")
public class ExtensionImplementationIndexTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "dom/index/extensionImplementation";
  }

  public void testKeysAndOffsets() {
    VirtualFile file = myFixture.copyFileToProject("plugin.xml", "META-INF/plugin.xml");
    String text = getText(file);
    Map<String, IntList> data = FileBasedIndex.getInstance().getFileData(ExtensionImplementationIndex.NAME, file, getProject());

    int myEp1 = text.indexOf("<myEp implementation=\"myPkg.MyImplementation\" os");
    int myEp2 = text.indexOf("<myEp implementation=\"myPkg.Outer$Inner$Deepest\"");
    int myEp3 = text.indexOf("<myEp implementation=\"myPkg.MyImplementation\"/>");
    int service = text.indexOf("<applicationService");
    int contributor = text.indexOf("<completion.contributor");
    int bean = text.indexOf("<myBeanEp");
    int dotted = text.indexOf("<dottedEp");

    assertSameElements(data.keySet(),
                       "myPkg.MyImplementation",
                       "myPkg.MyService", "myPkg.MyServiceImpl",
                       "myPkg.MyContributor",
                       "myPkg.MyBeanClass",
                       "myPkg.Deep$Nested", "myPkg.Deep",
                       "myPkg.Outer$Inner$Deepest", "myPkg.Outer$Inner", "myPkg.Outer",
                       "myPkg.Outer.DottedInner",
                       "ep:myEp", "ep:applicationService", "ep:contributor", "ep:myBeanEp", "ep:fold", "ep:dottedEp", "ep:ext");

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

  public void testExtensionPointShortNames() {
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
    assertTrue(ExtensionImplementationIndex.processExtensionsByEpShortName(getProject(), "contributor", scope, tag -> {
      names.add(tag.getName());
      return true;
    }));
    assertOrderedEquals(names, "contributor", "completion.contributor");

    names.clear();
    assertFalse(ExtensionImplementationIndex.processExtensionsByEpShortName(getProject(), "contributor", scope, tag -> {
      names.add(tag.getName());
      return false;
    }));
    assertSize(1, names);
  }

  public void testExtensionPointShortNamesAcrossFiles() {
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
    var scope = GlobalSearchScope.projectScope(getProject());
    var idsByFile = new ConcurrentHashMap<String, List<String>>();
    assertTrue(ExtensionImplementationIndex.processExtensionsByEpShortName(getProject(), "contributor", scope, tag -> {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      idsByFile.computeIfAbsent(tag.getContainingFile().getName(), _ -> new ArrayList<>()).add(tag.getAttributeValue("id"));
      return true;
    }));
    assertSize(fileCount, idsByFile.keySet());
    for (var ids : idsByFile.values()) {
      assertOrderedEquals(ids, expectedIds);
    }

    var processed = new AtomicInteger();
    assertFalse(ExtensionImplementationIndex.processExtensionsByEpShortName(getProject(), "contributor", scope, _ -> {
      processed.incrementAndGet();
      return false;
    }));
    assertTrue(processed.get() > 0 && processed.get() <= fileCount);
  }

  public void testExtensionPointLookupCancellation() {
    var file = myFixture.copyFileToProject("plugin.xml", "META-INF/plugin.xml");
    var scope = GlobalSearchScope.fileScope(getProject(), file);
    var indicator = new EmptyProgressIndicator();
    var processed = new AtomicInteger();
    assertThrows(ProcessCanceledException.class, () -> ProgressManager.getInstance().runProcess(() ->
      ExtensionImplementationIndex.processExtensionsByEpShortName(getProject(), "myEp", scope, _ -> {
        processed.incrementAndGet();
        indicator.cancel();
        return true;
      }), indicator));
    assertEquals(1, processed.get());
  }

  public void testProcessExtensions() {
    VirtualFile file = myFixture.copyFileToProject("plugin.xml", "META-INF/plugin.xml");
    String text = getText(file);
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());

    List<XmlTag> tags = new ArrayList<>();
    assertTrue(ExtensionImplementationIndex.processExtensions(getProject(), "myPkg.MyImplementation", scope, tags::add));
    assertSize(2, tags);
    for (XmlTag tag : tags) {
      assertEquals("myEp", tag.getName());
      assertEquals("myPkg.MyImplementation", tag.getAttributeValue("implementation"));
    }

    tags.clear();
    assertTrue(ExtensionImplementationIndex.processExtensions(getProject(), "myPkg.Outer", scope, tags::add));
    XmlTag outer = assertOneElement(tags);
    assertEquals(text.indexOf("<myEp implementation=\"myPkg.Outer$Inner$Deepest\""), outer.getTextOffset());
    assertEquals("myPkg.Outer$Inner$Deepest", outer.getAttributeValue("implementation"));

    tags.clear();
    assertTrue(ExtensionImplementationIndex.processExtensions(getProject(), "myPkg.Deep", scope, tags::add));
    assertEquals("myBeanEp", assertOneElement(tags).getName());

    tags.clear();
    assertFalse(ExtensionImplementationIndex.processExtensions(getProject(), "myPkg.MyImplementation", scope, tag -> {
      tags.add(tag);
      return false;
    }));
    assertSize(1, tags);

    assertTrue(ExtensionImplementationIndex.processExtensions(getProject(), "myPkg.MyClass.foo", scope, ExtensionImplementationIndexTest::unexpected));
    assertTrue(ExtensionImplementationIndex.processExtensions(getProject(), "myPkg.MyClass", scope, ExtensionImplementationIndexTest::unexpected));
  }

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

  public void testExactAndPrefixMatches() {
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
    assertTrue(ExtensionImplementationIndex.processExtensions(getProject(), "myPkg.Outer", scope, true, tag -> {
      ids.add(tag.getAttributeValue("id"));
      return true;
    }));
    assertOrderedEquals(ids, "prefixFirst", "exactFirst", "nested");

    ids.clear();
    assertTrue(ExtensionImplementationIndex.processExtensions(getProject(), "myPkg.Outer", scope, tag -> {
      ids.add(tag.getAttributeValue("id"));
      return true;
    }));
    assertOrderedEquals(ids, "prefix", "prefixFirst", "exactFirst", "nested");
  }

  public void testStrictMatchSkipsOuterPrefixes() {
    var file = myFixture.copyFileToProject("plugin.xml", "META-INF/plugin.xml");
    var scope = GlobalSearchScope.fileScope(getProject(), file);
    assertTrue(ExtensionImplementationIndex.processExtensions(getProject(), "myPkg.Outer", scope, true,
                                                             ExtensionImplementationIndexTest::unexpected));
    var tags = new ArrayList<XmlTag>();
    assertTrue(ExtensionImplementationIndex.processExtensions(getProject(), "myPkg.Outer$Inner$Deepest", scope, true, tags::add));
    assertSize(1, tags);
  }

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
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    return psiFile.getText();
  }

  private static void assertOffsets(Map<String, IntList> data, String key, int... expected) {
    IntList actual = data.get(key);
    assertNotNull("no key " + key, actual);
    List<Integer> expectedList = new ArrayList<>();
    for (int offset : expected) {
      assertTrue("fixture marker not found for " + key, offset >= 0);
      expectedList.add(offset);
    }
    IntList offsets = new IntArrayList(actual.size());
    for (int i = 0; i < actual.size(); i++) {
      offsets.add(actual.getInt(i) >>> 1);
    }
    assertOrderedEquals(key, offsets, expectedList);
  }
}
