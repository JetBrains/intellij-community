/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 20-Dec-2006
 */
package org.jetbrains.idea.devkit.build;

import com.intellij.compiler.ant.BuildTargetsFactory;
import com.intellij.compiler.ant.ModuleChunk;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.idea.devkit.build.ant.BuildJarTarget;

import java.io.DataOutputStream;
import java.io.OutputStream;

public class GenerateAntTest extends IdeaTestCase {

  public void testP1() throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
        final VirtualFile parent = myModule.getModuleFile().getParent();
        assertTrue(parent != null);
        model.setCompilerOutputPath(parent.getUrl() + "/classes");
        model.commit();
      }
    });
    checkJarTarget(new ModuleChunk(new Module[]{getModule()}));
  }

  private void checkJarTarget(ModuleChunk chunk) throws Exception {
    final StringBuffer targetText = new StringBuffer();
    final DataOutputStream dataOutput = new DataOutputStream(new OutputStream(){
      public void write(int b) {
        targetText.append((char)b);
      }
    });
    new BuildJarTarget(chunk, BuildTargetsFactory.getInstance().getDefaultOptions(getProject()), new PluginBuildConfiguration(getModule())).generate(dataOutput);
    final String expected = "<target name=\"plugin.build.jar."+ myModule.getName() +"\" description=\"Build plugin archive for module \'" + myModule.getName() + "\'\">\n" +
                            "  <jar destfile=\"${"+ myModule.getName() + ".path.jar}\" duplicate=\"preserve\">\n" +
                            "    <zipfileset dir=\"${module." + myModule.getName() + ".basedir}/classes\" prefix=\"\"/>\n" +
                            "    <zipfileset file=\"${module." + myModule.getName() + ".basedir}/META-INF/plugin.xml\" prefix=\"META-INF\"/>\n" +
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
    XmlTag genTag = getPsiManager().getElementFactory().createTagFromText(StringUtil.convertLineSeparators(generated));
    XmlTag expTag = getPsiManager().getElementFactory().createTagFromText(StringUtil.convertLineSeparators(expected));
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
      if (!Comparing.strEqual(gattribute.getText(), eattribute.getText())) return false;
    }
    return true;
  }
}