/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.build;

import com.intellij.compiler.ant.BuildTargetsFactory;
import com.intellij.compiler.ant.ModuleChunk;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.idea.devkit.build.ant.BuildJarTarget;

import java.io.PrintWriter;
import java.io.StringWriter;

public class GenerateAntTest extends IdeaTestCase {
  public void testP1() throws Exception {
    final VirtualFile parent = myModule.getModuleFile().getParent();
    assertNotNull(parent);
    PsiTestUtil.setCompilerOutputPath(myModule, parent.getUrl() + "/classes", false);
    checkJarTarget(new ModuleChunk(new Module[]{getModule()}));
  }

  private void checkJarTarget(ModuleChunk chunk) throws Exception {
    final StringWriter targetText = new StringWriter();
    final PrintWriter dataOutput = new PrintWriter(targetText);
    new BuildJarTarget(chunk, BuildTargetsFactory.getInstance().getDefaultOptions(getProject()), new PluginBuildConfiguration(getModule())).generate(dataOutput);
    dataOutput.flush();
    final String lowercased = StringUtil.toLowerCase(myModule.getName());
    final String expected = "<target name=\"plugin.build.jar."+
                            lowercased + "\" depends=\"compile.module." + lowercased +
                            "\" description=\"Build plugin archive for module &apos;" + myModule.getName() + "&apos;\">\n" +
                            "  <jar destfile=\"${"+ lowercased + ".plugin.path.jar}\" duplicate=\"preserve\">\n" +
                            "    <zipfileset dir=\"${module." + lowercased + ".basedir}/classes\"/>\n" +
                            "    <zipfileset file=\"${module." + lowercased + ".basedir}/META-INF/plugin.xml\" prefix=\"META-INF\"/>\n" +
                            "    <manifest>\n" +
                            "      <attribute name=\"Created-By\" value=\"IntelliJ IDEA\"/>\n" +
                            "      <attribute name=\"Manifest-Version\" value=\"1.0\"/>\n" +
                            "    </manifest>\n" +
                            "  </jar>\n" +
                            "</target>";
    checkBuildsEqual(targetText.toString(), expected);
  }

  private void checkBuildsEqual(String generated, String expected) throws IncorrectOperationException {
    final CodeStyleManager manager = CodeStyleManager.getInstance(myProject);
    XmlTag genTag = XmlElementFactory.getInstance(myProject).createTagFromText(StringUtil.convertLineSeparators(generated));
    XmlTag expTag = XmlElementFactory.getInstance(myProject).createTagFromText(StringUtil.convertLineSeparators(expected));
    if (!tagsEqual(genTag, expTag)) {
      genTag = (XmlTag)manager.reformat(manager.reformat(genTag));
      expTag = (XmlTag)manager.reformat(manager.reformat(expTag));
      assertEquals("Text mismatch: ", expTag.getText(), genTag.getText());
    }
  }

  private static boolean tagsEqual(XmlTag genTag, XmlTag expTag) {
    if (!attributesEqual(genTag, expTag)) return false;
    final XmlTag[] gsubTags = genTag.getSubTags();
    final XmlTag[] esubTags = expTag.getSubTags();
    if (gsubTags.length != esubTags.length) return false;
    for (int i = 0; i < esubTags.length; i++) {
      XmlTag esubTag = esubTags[i];
      XmlTag gsubTag = gsubTags[i];
      if (!tagsEqual(gsubTag, esubTag)) return false;
    }
    return true;
  }

  private static boolean attributesEqual(XmlTag genTag, XmlTag expTag) {
    final XmlAttribute[] gattributes = genTag.getAttributes();
    final XmlAttribute[] eattributes = expTag.getAttributes();
    if (gattributes.length != eattributes.length) return false;
    for (int i = 0; i < eattributes.length; i++) {
      XmlAttribute eattribute = eattributes[i];
      XmlAttribute gattribute = gattributes[i];
      // logical comparison of the attributes (namespace:localname and display value)
      if (!Comparing.strEqual(gattribute.getLocalName(), eattribute.getLocalName())) return false;
      if (!Comparing.strEqual(gattribute.getNamespace(), eattribute.getNamespace())) return false;
      if (!Comparing.strEqual(gattribute.getDisplayValue(), eattribute.getDisplayValue())) return false;
    }
    return true;
  }
}