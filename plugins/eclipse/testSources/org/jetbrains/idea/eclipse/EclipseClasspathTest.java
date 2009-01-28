/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 28-Nov-2008
 */
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import junit.framework.Assert;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.idea.eclipse.find.EclipseProjectFinder;
import org.jetbrains.idea.eclipse.reader.EclipseClasspathReader;
import org.jetbrains.idea.eclipse.writer.EclipseClasspathWriter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

public class EclipseClasspathTest extends IdeaTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File testRoot = new File(PathManager.getHomePath() + "/svnPlugins/eclipse/testData", "round");
    assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

    final File currentTestRoot = new File(testRoot, getTestName(true));
    assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

    FileUtil.copyDir(currentTestRoot, new File(getProject().getBaseDir().getPath()));


  }

  private void doTest() throws Exception {
    final String path =  getProject().getBaseDir().getPath()  + "/test";

    final Element classpathElement = JDOMUtil.loadDocument(new File(path, EclipseXml.DOT_CLASSPATH_EXT)).getRootElement();
    final Module module = ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      public Module compute() {
        return ModuleManager.getInstance(getProject())
          .newModule(path + "/" + EclipseProjectFinder.findProjectName(path) + IdeaXml.IML_EXT, StdModuleTypes.JAVA);
      }
    });
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    new EclipseClasspathReader(path, getProject())
      .readClasspath(rootModel, new ArrayList<String>(), new ArrayList<String>(), new HashSet<String>(), new HashSet<String>(), null, classpathElement);
    rootModel.commit();
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final Element resultClasspathElement = new Element(EclipseXml.CLASSPATH_TAG);
    new EclipseClasspathWriter(model).writeClasspath(resultClasspathElement, classpathElement);
    model.dispose();

    Assert.assertTrue(new String(JDOMUtil.printDocument(new Document(resultClasspathElement), "\n")), JDOMUtil.areElementsEqual(classpathElement, resultClasspathElement));
  }


  public void testWorkspaceOnly() throws Exception {
    doTest();
  }

  public void testPathVariables() throws Exception {
    doTest();
  }

  public void testJunit() throws Exception {
    doTest();
  }

  public void testSrcBinJRE() throws Exception {
    doTest();
  }

  public void testAccessrulez() throws Exception {
    doTest();
  }

  public void testSrcBinJREProject() throws Exception {
    doTest();
  }

  public void testSourceFolderOutput() throws Exception {
    doTest();
  }

  public void testMultipleSourceFolders() throws Exception {
    doTest();
  }

  public void testEmptySrc() throws Exception {
    doTest();
  }

}