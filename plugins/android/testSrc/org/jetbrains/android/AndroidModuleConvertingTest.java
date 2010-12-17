/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android;

import com.intellij.conversion.ConversionService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidModuleConvertingTest extends PlatformTestCase {
  private static void checkElement(final String filePath, final Element root) throws JDOMException, IOException {
    final File file = new File(getConvertingTestDataPath(), filePath);
    final Element expected = JDOMUtil.loadDocument(file).getRootElement();
    String expectedText = JDOMUtil.createOutputter("\n").outputString(expected);
    String actualText = JDOMUtil.createOutputter("\n").outputString(root);
    assertEquals(expectedText, actualText);
  }

  private static File getConvertingTestDataPath() {
    return new File(FileUtil.toSystemDependentName(AndroidTestCase.getAbsoluteTestDataPath() + "/converting"));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    clearGlobalLibraries();
  }

  private static void clearGlobalLibraries() {
    final LibraryTable.ModifiableModel model = LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
    for (Library library : model.getLibraries()) {
      model.removeLibrary(library);
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
  }

  public void testNewPlatform() throws Exception {
    final File moduleFile = createModuleFile("module1.iml");
    String testSdkPath = configureTestSdkPath(moduleFile);
    assert testSdkPath != null;
    doConvert();
    checkElement("module1_after.iml", JDOMUtil.loadDocument(moduleFile).getRootElement());
    Library library = LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraryByName("Android 1.5 Platform");
    VirtualFile[] jarFiles = library.getFiles(OrderRootType.CLASSES);
    assertEquals(1, jarFiles.length);
    assertEquals(testSdkPath + "/platforms/android-1.5/android.jar!/", jarFiles[0].getPath());
    VirtualFile[] javadocFiles = library.getFiles(JavadocOrderRootType.getInstance());
    assertEquals(1, javadocFiles.length);
    assertEquals(testSdkPath + "/docs/reference", javadocFiles[0].getPath());
  }

  private void doConvert() {
    ConversionService.getInstance().convertSilently(FileUtil.toSystemDependentName(((ProjectImpl)myProject).getStateStore().getLocation()));
  }

  private File createModuleFile(final String fileName) throws IOException {
    final File moduleFile = new File(FileUtil.toSystemDependentName(myModule.getModuleFilePath()));
    FileUtil.copy(new File(getConvertingTestDataPath(), fileName), moduleFile);
    myProject.save();
    return moduleFile;
  }

  public void testExistingPlatform() throws JDOMException, IOException {
    final File moduleFile = createModuleFile("module2.iml");
    String testSdkPath = configureTestSdkPath(moduleFile);
    assert testSdkPath != null;

    Library library = LibraryTablesRegistrar.getInstance().getLibraryTable().createLibrary("My Android SDK");
    final Library.ModifiableModel model = library.getModifiableModel();
    model.addRoot("jar://" + testSdkPath + "/platforms/android-1.5/android.jar!/", OrderRootType.CLASSES);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });

    int libCountBefore = LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraries().length;
    doConvert();
    checkElement("module2_after.iml", JDOMUtil.loadDocument(moduleFile).getRootElement());
    int libCountAfter = LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraries().length;
    assertEquals(libCountBefore, libCountAfter);
  }

  @Override
  protected File getIprFile() throws IOException {
    final File iprFile = super.getIprFile();
    FileUtil.writeToFile(iprFile, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><project version=\"4\" relativePaths=\"false\"></project>".getBytes());
    return iprFile;
  }

  @Nullable
  private static String configureTestSdkPath(File moduleFile) throws JDOMException, IOException {
    final Document document = JDOMUtil.loadDocument(moduleFile);
    for (Object o : document.getRootElement().getChildren("component")) {
      Element element = (Element)o;
      if ("FacetManager".equals(element.getAttributeValue("name"))) {
        for (Object option : element.getChild("facet").getChild("configuration").getChildren("option")) {
          Element optionElement = (Element)option;
          if ("SDK_PATH".equals(((Element)option).getAttributeValue("name"))) {
            String testSdkPath = new File(getConvertingTestDataPath().getParentFile(), "sdk1.5").getPath();
            optionElement.setAttribute("value", testSdkPath);
            JDOMUtil.writeDocument(document, moduleFile, SystemProperties.getLineSeparator());
            return testSdkPath.replace(File.separatorChar, '/');
          }
        }
      }
    }
    return null;
  }
}
