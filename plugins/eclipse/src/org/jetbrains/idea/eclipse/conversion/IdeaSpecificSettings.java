/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Date: 18-Dec-2009
 */
package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.config.CachedXmlDocumentSet;
import org.jetbrains.idea.eclipse.config.EclipseModuleManagerImpl;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.jetbrains.idea.eclipse.conversion.EPathUtil.areUrlsPointTheSame;

/**
 * Read/write .eml
 */
public class IdeaSpecificSettings extends AbstractIdeaSpecificSettings<ModifiableRootModel, ContentEntry, Sdk> {
  @NonNls private static final String RELATIVE_MODULE_SRC = "relative-module-src";
  @NonNls private static final String RELATIVE_MODULE_CLS = "relative-module-cls";
  @NonNls private static final String RELATIVE_MODULE_JAVADOC = "relative-module-javadoc";
  @NonNls private static final String PROJECT_RELATED = "project-related";

  @NonNls private static final String SRCROOT_ATTR = "srcroot";
  @NonNls private static final String SRCROOT_BIND_ATTR = "bind";
  private static final Logger LOG = Logger.getInstance("#" + IdeaSpecificSettings.class.getName());
  @NonNls private static final String JAVADOCROOT_ATTR = "javadocroot_attr";
  public static final String INHERIT_JDK = "inheritJdk";

  private IdeaSpecificSettings() {
  }

  public static void readIDEASpecific(ModifiableRootModel model, CachedXmlDocumentSet documentSet, String eml) throws InvalidDataException, IOException, JDOMException {
    new IdeaSpecificSettings().readIDEASpecific(documentSet.read(eml).getRootElement(), model, null, new HashMap<String, String>());
  }

  @Override
  protected void readLibraryLevels(Element root, Map<String, String> levels) {
  }

  @Override
  protected ContentEntry[] getEntries(ModifiableRootModel model) {
    return model.getContentEntries();
  }

  @Override
  protected ContentEntry createContentEntry(ModifiableRootModel model, final String url) {
    return model.addContentEntry(url);
  }

  @Override
  protected void setupLibraryRoots(Element root, ModifiableRootModel model) {
    for (Object o : root.getChildren("lib")) {
      Element libElement = (Element)o;
      final String libName = libElement.getAttributeValue("name");
      Library libraryByName = model.getModuleLibraryTable().getLibraryByName(libName);
      if (libraryByName != null) {
        appendLibraryScope(model, libElement, libraryByName);
        final Library.ModifiableModel modifiableModel = libraryByName.getModifiableModel();
        replaceCollapsedByEclipseSourceRoots(libElement, modifiableModel);
        for (Object r : libElement.getChildren(JAVADOCROOT_ATTR)) {
          final String url = ((Element)r).getAttributeValue("url");
          modifiableModel.addRoot(url, JavadocOrderRootType.getInstance());
        }
        replaceModuleRelatedRoots(model.getProject(), modifiableModel, libElement, OrderRootType.SOURCES, RELATIVE_MODULE_SRC);
        replaceModuleRelatedRoots(model.getProject(), modifiableModel, libElement, OrderRootType.CLASSES, RELATIVE_MODULE_CLS);
        replaceModuleRelatedRoots(model.getProject(), modifiableModel, libElement, JavadocOrderRootType.getInstance(), RELATIVE_MODULE_JAVADOC);
        modifiableModel.commit();
      } else {
        final Library library = EclipseClasspathReader.findLibraryByName(model.getProject(), libName);
        if (library != null) {
          appendLibraryScope(model, libElement, library);
        }
      }
    }
  }

  @Override
  protected void setupJdk(Element root, ModifiableRootModel model, @Nullable Sdk sdk) {
    final String inheritJdk = root.getAttributeValue(INHERIT_JDK);
    if (inheritJdk != null && Boolean.parseBoolean(inheritJdk)) {
      model.inheritSdk();
    } else {
      final String jdkName = root.getAttributeValue("jdk");
      if (jdkName != null) {
        final Sdk jdkByName = ProjectJdkTable.getInstance().findJdk(jdkName);
        if (jdkByName != null) {
          model.setSdk(jdkByName);
        }
      }
    }
  }

  @Override
  protected void setupCompilerOutputs(Element root, ModifiableRootModel model) {
    final CompilerModuleExtension compilerModuleExtension = model.getModuleExtension(CompilerModuleExtension.class);
    final Element testOutputElement = root.getChild(IdeaXml.OUTPUT_TEST_TAG);
    if (testOutputElement != null) {
      compilerModuleExtension.setCompilerOutputPathForTests(testOutputElement.getAttributeValue(IdeaXml.URL_ATTR));
    }

    final String inheritedOutput = root.getAttributeValue(IdeaXml.INHERIT_COMPILER_OUTPUT_ATTR);
    if (inheritedOutput != null && Boolean.valueOf(inheritedOutput).booleanValue()) {
      compilerModuleExtension.inheritCompilerOutputPath(true);
    }

    compilerModuleExtension.setExcludeOutput(root.getChild(IdeaXml.EXCLUDE_OUTPUT_TAG) != null);
  }

  @Override
  protected void readLanguageLevel(Element root, ModifiableRootModel model) throws InvalidDataException {
    model.getModuleExtension(LanguageLevelModuleExtension.class).readExternal(root);
  }

  @Override
  protected void expandElement(Element root, ModifiableRootModel model) {
    PathMacroManager.getInstance(model.getModule()).expandPaths(root);
  }

  @Override
  protected void overrideModulesScopes(Element root, ModifiableRootModel model) {
    for (Object o : root.getChildren("module")) {
      final String moduleName = ((Element)o).getAttributeValue("name");
      final String scope = ((Element)o).getAttributeValue("scope");
      if (scope != null) {
        for (OrderEntry entry : model.getOrderEntries()) {
          if (entry instanceof ModuleOrderEntry && Comparing.strEqual(((ModuleOrderEntry)entry).getModuleName(), moduleName)) {
            ((ModuleOrderEntry)entry).setScope(DependencyScope.valueOf(scope));
            break;
          }
        }
      }
    }
  }

  private static void appendLibraryScope(ModifiableRootModel model, Element libElement, Library libraryByName) {
    final LibraryOrderEntry libraryOrderEntry = model.findLibraryOrderEntry(libraryByName);
    if (libraryOrderEntry != null) {
      final String scopeAttribute = libElement.getAttributeValue("scope");
      libraryOrderEntry.setScope(scopeAttribute == null ? DependencyScope.COMPILE : DependencyScope.valueOf(scopeAttribute));
    }
  }

  /**
   * Eclipse detect sources inside zip automatically while IDEA doesn't.
   * So .eml contains expanded roots which should replace zip root read from .classpath
   */
  private static void replaceCollapsedByEclipseSourceRoots(Element libElement, Library.ModifiableModel modifiableModel) {
    String[] srcUrlsFromClasspath = modifiableModel.getUrls(OrderRootType.SOURCES);
    LOG.assertTrue(srcUrlsFromClasspath.length <= 1);
    final String eclipseUrl = srcUrlsFromClasspath.length > 0 ? srcUrlsFromClasspath[0] : null;
    for (Object r : libElement.getChildren(SRCROOT_ATTR)) {
      final String url = ((Element)r).getAttributeValue("url");
      final String bindAttr = ((Element)r).getAttributeValue(SRCROOT_BIND_ATTR);
      boolean notBind = bindAttr != null && !Boolean.parseBoolean(bindAttr);
      if (notBind) {
        modifiableModel.addRoot(url, OrderRootType.SOURCES);
      }
      else if (eclipseUrl != null && areUrlsPointTheSame(url, eclipseUrl) && !Comparing.strEqual(url, eclipseUrl)) {  //todo lost already configured additional src roots
        modifiableModel.addRoot(url, OrderRootType.SOURCES);
        if (srcUrlsFromClasspath != null && srcUrlsFromClasspath.length == 1) {  //remove compound root
          modifiableModel.removeRoot(eclipseUrl, OrderRootType.SOURCES);
          srcUrlsFromClasspath = null;
        }
      }
    }
  }


  @Override
  public void readContentEntry(Element root, ContentEntry entry, ModifiableRootModel model) {
    final SourceFolder[] folders = entry.getSourceFolders();
    final String[] sourceFoldersUrls = new String[folders.length];
    for (int i = 0; i < folders.length; i++) {
      final SourceFolder folder = folders[i];
      sourceFoldersUrls[i] = folder.getUrl();
      entry.removeSourceFolder(folder);
    }

    final boolean[] testFolders = new boolean[sourceFoldersUrls.length];
    for (Object o : root.getChildren(IdeaXml.TEST_FOLDER_TAG)) {
      final String url = ((Element)o).getAttributeValue(IdeaXml.URL_ATTR);
      for (int i = 0; i < sourceFoldersUrls.length; i++) {
        if (Comparing.strEqual(sourceFoldersUrls[i], url)) {
          testFolders[i] = true;
          break;
        }
      }
    }

    for (int i = 0; i < sourceFoldersUrls.length; i++) {
      entry.addSourceFolder(sourceFoldersUrls[i], testFolders[i]);
    }
    
    for (Object o : root.getChildren(IdeaXml.PACKAGE_PREFIX_TAG)) {
      Element ppElement = (Element)o;
      final String prefix = ppElement.getAttributeValue(IdeaXml.PACKAGE_PREFIX_VALUE_ATTR);
      final String url = ppElement.getAttributeValue(IdeaXml.URL_ATTR);
      for (SourceFolder folder : entry.getSourceFolders()) {
        if (Comparing.strEqual(folder.getUrl(), url)) {
          folder.setPackagePrefix(prefix);
          break;
        }
      }
    }
    
    final String url = entry.getUrl();
    for (Object o : root.getChildren(IdeaXml.EXCLUDE_FOLDER_TAG)) {
      final String excludeUrl = ((Element)o).getAttributeValue(IdeaXml.URL_ATTR);
      if (FileUtil.isAncestor(new File(url), new File(excludeUrl), false)) { //check if it is excluded manually
        entry.addExcludeFolder(excludeUrl);
      }
    }
  }

  public static boolean writeIDEASpecificClasspath(final Element root, ModuleRootModel model) throws WriteExternalException {

    boolean isModified = false;

    final CompilerModuleExtension compilerModuleExtension = model.getModuleExtension(CompilerModuleExtension.class);

    if (compilerModuleExtension.getCompilerOutputUrlForTests() != null) {
      final Element pathElement = new Element(IdeaXml.OUTPUT_TEST_TAG);
      pathElement.setAttribute(IdeaXml.URL_ATTR, compilerModuleExtension.getCompilerOutputUrlForTests());
      root.addContent(pathElement);
      isModified = true;
    }
    if (compilerModuleExtension.isCompilerOutputPathInherited()) {
      root.setAttribute(IdeaXml.INHERIT_COMPILER_OUTPUT_ATTR, String.valueOf(true));
      isModified = true;
    }
    if (compilerModuleExtension.isExcludeOutput()) {
      root.addContent(new Element(IdeaXml.EXCLUDE_OUTPUT_TAG));
      isModified = true;
    }

    final LanguageLevelModuleExtension languageLevelModuleExtension = model.getModuleExtension(LanguageLevelModuleExtension.class);
    final LanguageLevel languageLevel = languageLevelModuleExtension.getLanguageLevel();
    if (languageLevel != null) {
      languageLevelModuleExtension.writeExternal(root);
      isModified = true;
    }

    for (ContentEntry entry : model.getContentEntries()) {
      final Element contentEntryElement = new Element(IdeaXml.CONTENT_ENTRY_TAG);
      contentEntryElement.setAttribute(IdeaXml.URL_ATTR, entry.getUrl());
      root.addContent(contentEntryElement);
      for (SourceFolder sourceFolder : entry.getSourceFolders()) {
        if (sourceFolder.isTestSource()) {
          Element element = new Element(IdeaXml.TEST_FOLDER_TAG);
          contentEntryElement.addContent(element);
          element.setAttribute(IdeaXml.URL_ATTR, sourceFolder.getUrl());
          isModified = true;
        }
        final String packagePrefix = sourceFolder.getPackagePrefix();
        if (!StringUtil.isEmptyOrSpaces(packagePrefix)) {
          Element element = new Element(IdeaXml.PACKAGE_PREFIX_TAG);
          contentEntryElement.addContent(element);
          element.setAttribute(IdeaXml.URL_ATTR, sourceFolder.getUrl());
          element.setAttribute(IdeaXml.PACKAGE_PREFIX_VALUE_ATTR, packagePrefix);
          isModified = true;
        }
      }

      final VirtualFile entryFile = entry.getFile();
      for (ExcludeFolder excludeFolder : entry.getExcludeFolders()) {
        final String exludeFolderUrl = excludeFolder.getUrl();
        final VirtualFile excludeFile = excludeFolder.getFile();
        if (entryFile == null || excludeFile == null || VfsUtilCore.isAncestor(entryFile, excludeFile, false)) {
          Element element = new Element(IdeaXml.EXCLUDE_FOLDER_TAG);
          contentEntryElement.addContent(element);
          element.setAttribute(IdeaXml.URL_ATTR, exludeFolderUrl);
          isModified = true;
        }
      }
    }

    final Map<String, String> libLevels = new LinkedHashMap<String, String>();
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        final DependencyScope scope = ((ModuleOrderEntry)entry).getScope();
        if (!scope.equals(DependencyScope.COMPILE)) {
          Element element = new Element("module");
          element.setAttribute("name", ((ModuleOrderEntry)entry).getModuleName());
          element.setAttribute("scope", scope.name());
          root.addContent(element);
          isModified = true;
        }
      }
      if (entry instanceof JdkOrderEntry) {
        final Sdk jdk = ((JdkOrderEntry)entry).getJdk();
        if (EclipseModuleManagerImpl.getInstance(entry.getOwnerModule()).getInvalidJdk() != null || jdk != null) {
          if (entry instanceof InheritedJdkOrderEntry) {
            root.setAttribute(INHERIT_JDK, "true");
          } else {
            root.setAttribute("jdk", ((JdkOrderEntry)entry).getJdkName());
            if (jdk != null) {
              root.setAttribute("jdk_type", jdk.getSdkType().getName());
            }
          }
          isModified = true;
        }
      }
      if (!(entry instanceof LibraryOrderEntry)) continue;

      final Element element = new Element("lib");
      element.setAttribute("name", entry.getPresentableName());
      final LibraryOrderEntry libraryEntry = (LibraryOrderEntry)entry;
      final DependencyScope scope = libraryEntry.getScope();
      element.setAttribute("scope", scope.name());
      if (libraryEntry.isModuleLevel()) {
        final String[] urls = libraryEntry.getRootUrls(OrderRootType.SOURCES);
        String eclipseUrl = null;
        if (urls.length > 0) {
          eclipseUrl = urls[0];
          final int jarSeparatorIdx = urls[0].indexOf(JarFileSystem.JAR_SEPARATOR);
          if (jarSeparatorIdx > -1) {
            eclipseUrl = eclipseUrl.substring(0, jarSeparatorIdx);
          }
        }
        for (String url : urls) {
          Element srcElement = new Element(SRCROOT_ATTR);
          srcElement.setAttribute("url", url);
          if (!areUrlsPointTheSame(url, eclipseUrl)) {
            srcElement.setAttribute(SRCROOT_BIND_ATTR, String.valueOf(false));
          }
          element.addContent(srcElement);
        }

        final String[] javadocUrls = libraryEntry.getRootUrls(JavadocOrderRootType.getInstance());
        for (int i = 1; i < javadocUrls.length;  i++) {
          Element javadocElement = new Element(JAVADOCROOT_ATTR);
          javadocElement.setAttribute("url", javadocUrls[i]);
          element.addContent(javadocElement);
        }

        for (String srcUrl : libraryEntry.getRootUrls(OrderRootType.SOURCES)) {
          appendModuleRelatedRoot(element, srcUrl, RELATIVE_MODULE_SRC, model);
        }

        for (String classesUrl : libraryEntry.getRootUrls(OrderRootType.CLASSES)) {
          appendModuleRelatedRoot(element, classesUrl, RELATIVE_MODULE_CLS, model);
        }

        for (String javadocUrl : libraryEntry.getRootUrls(JavadocOrderRootType.getInstance())) {
          appendModuleRelatedRoot(element, javadocUrl, RELATIVE_MODULE_JAVADOC, model);
        }

        if (!element.getChildren().isEmpty()) {
          root.addContent(element);
          isModified = true;
          continue;
        }
      } else {
        final String libraryLevel = libraryEntry.getLibraryLevel();
        if (!LibraryTablesRegistrar.APPLICATION_LEVEL.equals(libraryLevel)) {
          libLevels.put(libraryEntry.getLibraryName(), libraryLevel);
        }
      }
      if (!scope.equals(DependencyScope.COMPILE)) {
        root.addContent(element);
        isModified = true;
      }
    }

    if (!libLevels.isEmpty()) {
      final Element libLevelsElement = new Element("levels");
      for (String libName : libLevels.keySet()) {
        final Element libElement = new Element("level");
        libElement.setAttribute("name", libName);
        libElement.setAttribute("value", libLevels.get(libName));
        libLevelsElement.addContent(libElement);
      }
      root.addContent(libLevelsElement);
    }

    PathMacroManager.getInstance(model.getModule()).collapsePaths(root);

    return isModified;
  }

  public static void replaceModuleRelatedRoots(final Project project,
                                               final Library.ModifiableModel modifiableModel, final Element libElement,
                                               final OrderRootType orderRootType, final String relativeModuleName) {
    final List<String> urls = new ArrayList<String>(Arrays.asList(modifiableModel.getUrls(orderRootType)));
    for (Object r : libElement.getChildren(relativeModuleName)) {
      final String root = PathMacroManager.getInstance(project).expandPath(((Element)r).getAttributeValue(PROJECT_RELATED));
      for (Iterator<String> iterator = urls.iterator(); iterator.hasNext();) {
        final String url = iterator.next();
        if (areUrlsPointTheSame(root, url)) {
          iterator.remove();
          modifiableModel.removeRoot(url, orderRootType);
          modifiableModel.addRoot(root, orderRootType);
          break;
        }
      }
    }
  }

  public static boolean appendModuleRelatedRoot(Element element, String classesUrl, final String rootName, ModuleRootModel model) {
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(classesUrl);
    final Project project = model.getModule().getProject();
    if (file != null) {
      if (file.getFileSystem() instanceof JarFileSystem) {
        file = JarFileSystem.getInstance().getVirtualFileForJar(file);
        assert file != null;
      }
      final Module module = ModuleUtilCore.findModuleForFile(file, project);
      if (module != null) {
        return appendRelatedToModule(element, classesUrl, rootName, file, module);
      } else if (ProjectRootManager.getInstance(project).getFileIndex().isIgnored(file)) {
        for (Module aModule : ModuleManager.getInstance(project).getModules()) {
          if (appendRelatedToModule(element, classesUrl, rootName, file, aModule)) return true;
        }
      }
    }
    return false;
  }

  private static boolean appendRelatedToModule(Element element, String classesUrl, String rootName, VirtualFile file, Module module) {
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile contentRoot : contentRoots) {
      if (VfsUtilCore.isAncestor(contentRoot, file, false)) {
        final Element clsElement = new Element(rootName);
        clsElement.setAttribute(PROJECT_RELATED, PathMacroManager.getInstance(module.getProject()).collapsePath(classesUrl));
        element.addContent(clsElement);
        return true;
      }
    }
    return false;
  }
}
