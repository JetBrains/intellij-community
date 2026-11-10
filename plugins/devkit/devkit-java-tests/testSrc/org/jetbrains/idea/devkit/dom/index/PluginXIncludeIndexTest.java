// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.idea.devkit.dom.index.PluginXIncludeIndex.XIncludeEntry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class PluginXIncludeIndexTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testKeysAndEntries() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.addFileToProject("META-INF/plugin.xml", """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <xi:include href="/META-INF/whole.xml"/>
        <xi:include href="children.xml" xpointer="xpointer(/idea-plugin/*)"/>
        <xi:include href="section.xml" xpointer="xpointer(/idea-plugin/extensions/*)"/>
        <extensions defaultExtensionNs="com.intellij">
          <xi:include href="nested.xml"/>
        </extensions>
        <xi:include href="sub/whole.xml"/>
        <xi:include href=" "/>
      </idea-plugin>
      """).getVirtualFile();
    Map<String, List<XIncludeEntry>> data = ReadAction.computeBlocking(() -> FileBasedIndex.getInstance().getFileData(PluginXIncludeIndex.NAME, file, myFixture.getProject()));

    assertThat(data.keySet()).containsOnly("whole.xml", "children.xml", "section.xml", "nested.xml");
    assertThat(data.get("whole.xml")).containsOnly(new XIncludeEntry("/META-INF/whole.xml", true), new XIncludeEntry("sub/whole.xml", true));
    assertThat(data.get("children.xml")).containsOnly(new XIncludeEntry("children.xml", true));
    assertThat(data.get("section.xml")).containsOnly(new XIncludeEntry("section.xml", false));
    assertThat(data.get("nested.xml")).containsOnly(new XIncludeEntry("nested.xml", false));
  }

  @Test
  public void testUnboundXiPrefix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.addFileToProject("META-INF/plugin.xml", """
      <idea-plugin>
        <xi:include href="target.xml"/>
      </idea-plugin>
      """).getVirtualFile();
    Map<String, List<XIncludeEntry>> data = ReadAction.computeBlocking(() -> FileBasedIndex.getInstance().getFileData(PluginXIncludeIndex.NAME, file, myFixture.getProject()));

    assertThat(data.keySet()).containsOnly("target.xml");
  }

  @Test
  public void testNonPluginXmlIsNotIndexed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.addFileToProject("META-INF/other.xml", """
      <root xmlns:xi="http://www.w3.org/2001/XInclude">
        <xi:include href="target.xml"/>
      </root>
      """).getVirtualFile();

    ReadAction.runBlocking(() -> {
      assertThat(FileBasedIndex.getInstance().getFileData(PluginXIncludeIndex.NAME, file, myFixture.getProject()).keySet()).isNullOrEmpty();
    });
  }

  @Test
  public void testGetIncludes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile includer = myFixture.addFileToProject("META-INF/plugin.xml", """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <xi:include href="/META-INF/target.xml"/>
      </idea-plugin>
      """).getVirtualFile();
    myFixture.addFileToProject("META-INF/unrelated.xml", """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <xi:include href="/META-INF/other.xml"/>
      </idea-plugin>
      """);

    Map<VirtualFile, List<XIncludeEntry>> includes = ReadAction.computeBlocking(() -> PluginXIncludeIndex.getIncludes("target.xml", GlobalSearchScope.projectScope(myFixture.getProject())));

    assertEquals(Map.of(includer, List.of(new XIncludeEntry("/META-INF/target.xml", true))), includes);
  }

  @Test
  public void testValueExternalizerRoundTrip() throws IOException {
    var externalizer = new PluginXIncludeIndex().getValueExternalizer();
    var value = List.of(new XIncludeEntry("/META-INF/a.xml", true), new XIncludeEntry("b.xml", false));
    var bytes = new ByteArrayOutputStream();
    externalizer.save(new DataOutputStream(bytes), value);

    assertEquals(value, externalizer.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
  }
}
