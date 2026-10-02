// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.idea.devkit.dom.index.PluginXIncludeIndex.XIncludeEntry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PluginXIncludeIndexTest extends LightJavaCodeInsightFixtureTestCase {

  public void testKeysAndEntries() {
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
    Map<String, List<XIncludeEntry>> data = FileBasedIndex.getInstance().getFileData(PluginXIncludeIndex.NAME, file, getProject());

    assertSameElements(data.keySet(), "whole.xml", "children.xml", "section.xml", "nested.xml");
    assertSameElements(data.get("whole.xml"), new XIncludeEntry("/META-INF/whole.xml", true), new XIncludeEntry("sub/whole.xml", true));
    assertSameElements(data.get("children.xml"), new XIncludeEntry("children.xml", true));
    assertSameElements(data.get("section.xml"), new XIncludeEntry("section.xml", false));
    assertSameElements(data.get("nested.xml"), new XIncludeEntry("nested.xml", false));
  }

  public void testUnboundXiPrefix() {
    VirtualFile file = myFixture.addFileToProject("META-INF/plugin.xml", """
      <idea-plugin>
        <xi:include href="target.xml"/>
      </idea-plugin>
      """).getVirtualFile();
    Map<String, List<XIncludeEntry>> data = FileBasedIndex.getInstance().getFileData(PluginXIncludeIndex.NAME, file, getProject());

    assertSameElements(data.keySet(), "target.xml");
  }

  public void testNonPluginXmlIsNotIndexed() {
    VirtualFile file = myFixture.addFileToProject("META-INF/other.xml", """
      <root xmlns:xi="http://www.w3.org/2001/XInclude">
        <xi:include href="target.xml"/>
      </root>
      """).getVirtualFile();

    assertEmpty(FileBasedIndex.getInstance().getFileData(PluginXIncludeIndex.NAME, file, getProject()).keySet());
  }

  public void testGetIncludes() {
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

    Map<VirtualFile, List<XIncludeEntry>> includes = PluginXIncludeIndex.getIncludes("target.xml", GlobalSearchScope.projectScope(getProject()));

    assertEquals(Map.of(includer, List.of(new XIncludeEntry("/META-INF/target.xml", true))), includes);
  }

  public void testValueExternalizerRoundTrip() throws IOException {
    var externalizer = new PluginXIncludeIndex().getValueExternalizer();
    var value = List.of(new XIncludeEntry("/META-INF/a.xml", true), new XIncludeEntry("b.xml", false));
    var bytes = new ByteArrayOutputStream();
    externalizer.save(new DataOutputStream(bytes), value);

    assertEquals(value, externalizer.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
  }
}
