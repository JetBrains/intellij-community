// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.eclipse.conversion;

import com.intellij.configurationStore.ComponentSerializationUtil;
import com.intellij.configurationStore.XmlSerializer;
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.config.EclipseModuleManagerImpl;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;

import java.io.File;
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
  private static final Logger LOG = Logger.getInstance(IdeaSpecificSettings.class);
  @NonNls private static final String JAVADOCROOT_ATTR = "javadocroot_attr";
  public static final String INHERIT_JDK = "inheritJdk";

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
    for (Element libElement : root.getChildren("lib")) {
      final String libName = libElement.getAttributeValue("name");
      Library libraryByName = model.getModuleLibraryTable().getLibraryByName(libName);
      if (libraryByName != null) {
        appendLibraryScope(model, libElement, libraryByName);
        final Library.ModifiableModel modifiableModel = libraryByName.getModifiableModel();
        replaceCollapsedByEclipseSourceRoots(libElement, modifiableModel);
        for (Element r : libElement.getChildren(JAVADOCROOT_ATTR)) {
          modifiableModel.addRoot(r.getAttributeValue("url"), JavadocOrderRootType.getInstance());
        }
        replaceModuleRelatedRoots(model.getProject(), modifiableModel, libElement, OrderRootType.SOURCES, RELATIVE_MODULE_SRC);
        replaceModuleRelatedRoots(model.getProject(), modifiableModel, libElement, OrderRootType.CLASSES, RELATIVE_MODULE_CLS);
        replaceModuleRelatedRoots(model.getProject(), modifiableModel, libElement, JavadocOrderRootType.getInstance(), RELATIVE_MODULE_JAVADOC);
        modifiableModel.commit();
      }
      else {
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
    }
    else {
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

    final String inheritedOutput = root.getAttributeValue(JpsJavaModelSerializerExtension.INHERIT_COMPILER_OUTPUT_ATTRIBUTE);
    if (inheritedOutput != null && Boolean.valueOf(inheritedOutput).booleanValue()) {
      compilerModuleExtension.inheritCompilerOutputPath(true);
    }

    compilerModuleExtension.setExcludeOutput(root.getChild(IdeaXml.EXCLUDE_OUTPUT_TAG) != null);
  }

  @Override
  protected void readLanguageLevel(Element root, ModifiableRootModel model) {
    LanguageLevelModuleExtensionImpl component = model.getModuleExtension(LanguageLevelModuleExtensionImpl.class);
    component.loadState(XmlSerializer.deserialize(root, ComponentSerializationUtil.getStateClass((component.getClass()))));
  }

  @Override
  protected void expandElement(Element root, ModifiableRootModel model) {
    PathMacroManager.getInstance(model.getModule()).expandPaths(root);
  }

  @Override
  protected void overrideModulesScopes(Element root, ModifiableRootModel model) {
    for (Element o : root.getChildren("module")) {
      String moduleName = o.getAttributeValue("name");
      String scope = o.getAttributeValue("scope");
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
    for (Element r : libElement.getChildren(SRCROOT_ATTR)) {
      final String url = r.getAttributeValue("url");
      final String bindAttr = r.getAttributeValue(SRCROOT_BIND_ATTR);
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
    for (Element o : root.getChildren(IdeaXml.TEST_FOLDER_TAG)) {
      final String url = o.getAttributeValue(IdeaXml.URL_ATTR);
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
    
    for (Element ppElement : root.getChildren(IdeaXml.PACKAGE_PREFIX_TAG)) {
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
    for (Element o : root.getChildren(IdeaXml.EXCLUDE_FOLDER_TAG)) {
      final String excludeUrl = o.getAttributeValue(IdeaXml.URL_ATTR);
      if (FileUtil.isAncestor(new File(url), new File(excludeUrl), false)) { //check if it is excluded manually
        entry.addExcludeFolder(excludeUrl);
      }
    }
  }

  public static boolean writeIdeaSpecificClasspath(@NotNull Element root, @NotNull ModuleRootModel model) {
    boolean isModified = false;

    CompilerModuleExtension compilerModuleExtension = model.getModuleExtension(CompilerModuleExtension.class);
    if (compilerModuleExtension.getCompilerOutputUrlForTests() != null) {
      final Element pathElement = new Element(IdeaXml.OUTPUT_TEST_TAG);
      pathElement.setAttribute(IdeaXml.URL_ATTR, compilerModuleExtension.getCompilerOutputUrlForTests());
      root.addContent(pathElement);
      isModified = true;
    }
    if (compilerModuleExtension.isCompilerOutputPathInherited()) {
      root.setAttribute(JpsJavaModelSerializerExtension.INHERIT_COMPILER_OUTPUT_ATTRIBUTE, String.valueOf(true));
      isModified = true;
    }
    if (compilerModuleExtension.isExcludeOutput()) {
      root.addContent(new Element(IdeaXml.EXCLUDE_OUTPUT_TAG));
      isModified = true;
    }

    LanguageLevelModuleExtensionImpl languageLevelModuleExtension = model.getModuleExtension(LanguageLevelModuleExtensionImpl.class);
    if (languageLevelModuleExtension.getLanguageLevel() != null) {
      //noinspection ConstantConditions
      XmlSerializer.serializeStateInto(languageLevelModuleExtension, root);
      isModified = true;
    }

    for (ContentEntry entry : model.getContentEntries()) {
      Element contentEntryElement = new Element(IdeaXml.CONTENT_ENTRY_TAG);
      contentEntryElement.setAttribute(IdeaXml.URL_ATTR, entry.getUrl());
      root.addContent(contentEntryElement);
      for (SourceFolder sourceFolder : entry.getSourceFolders()) {
        if (sourceFolder.isTestSource()) {
          Element element = new Element(IdeaXml.TEST_FOLDER_TAG);
          contentEntryElement.addContent(element);
          element.setAttribute(IdeaXml.URL_ATTR, sourceFolder.getUrl());
          isModified = true;
        }
        String packagePrefix = sourceFolder.getPackagePrefix();
        if (!StringUtil.isEmptyOrSpaces(packagePrefix)) {
          Element element = new Element(IdeaXml.PACKAGE_PREFIX_TAG);
          contentEntryElement.addContent(element);
          element.setAttribute(IdeaXml.URL_ATTR, sourceFolder.getUrl());
          element.setAttribute(IdeaXml.PACKAGE_PREFIX_VALUE_ATTR, packagePrefix);
          isModified = true;
        }
      }

      VirtualFile entryFile = entry.getFile();
      for (ExcludeFolder excludeFolder : entry.getExcludeFolders()) {
        VirtualFile excludeFile = excludeFolder.getFile();
        if (entryFile == null || excludeFile == null || VfsUtilCore.isAncestor(entryFile, excludeFile, false)) {
          Element element = new Element(IdeaXml.EXCLUDE_FOLDER_TAG);
          contentEntryElement.addContent(element);
          element.setAttribute(IdeaXml.URL_ATTR, excludeFolder.getUrl());
          isModified = true;
        }
      }
    }

    Map<String, String> libLevels = new LinkedHashMap<>();
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
          }
          else {
            root.setAttribute("jdk", ((JdkOrderEntry)entry).getJdkName());
            if (jdk != null) {
              root.setAttribute("jdk_type", jdk.getSdkType().getName());
            }
          }
          isModified = true;
        }
      }
      if (!(entry instanceof LibraryOrderEntry)) continue;

      Element element = new Element("lib");
      LibraryOrderEntry libraryEntry = (LibraryOrderEntry)entry;

      String libraryName = ((LibraryOrderEntry)entry).getLibraryName();
      if (libraryName == null) {
        final String[] urls = libraryEntry.getRootUrls(OrderRootType.CLASSES);
        if (urls.length > 0) {
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(urls[0]);
          final VirtualFile fileForJar = JarFileSystem.getInstance().getVirtualFileForJar(file);
          if (fileForJar != null) {
            file = fileForJar;
          }
          libraryName = file != null ? file.getName() : null;
        }
        if (libraryName == null) {
          libraryName = libraryEntry.getPresentableName();
        }
      }

      element.setAttribute("name", libraryName);
      DependencyScope scope = libraryEntry.getScope();
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
        for (int i = 1; i < javadocUrls.length; i++) {
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
      }
      else {
        String libraryLevel = libraryEntry.getLibraryLevel();
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
      Element libLevelsElement = new Element("levels");
      for (String libName : libLevels.keySet()) {
        Element libElement = new Element("level");
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
    final List<String> urls = new ArrayList<>(Arrays.asList(modifiableModel.getUrls(orderRootType)));
    for (Element r : libElement.getChildren(relativeModuleName)) {
      final String root = PathMacroManager.getInstance(project).expandPath(r.getAttributeValue(PROJECT_RELATED));
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

  public static void appendModuleRelatedRoot(Element element, String classesUrl, final String rootName, ModuleRootModel model) {
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(classesUrl);
    if (file == null) {
      return;
    }

    final Project project = model.getModule().getProject();
    if (file.getFileSystem() instanceof JarFileSystem) {
      file = JarFileSystem.getInstance().getVirtualFileForJar(file);
      assert file != null;
    }
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module != null) {
      appendRelatedToModule(element, classesUrl, rootName, file, module);
    }
    else if (ProjectRootManager.getInstance(project).getFileIndex().isExcluded(file)) {
      for (Module aModule : ModuleManager.getInstance(project).getModules()) {
        if (appendRelatedToModule(element, classesUrl, rootName, file, aModule)) {
          return;
        }
      }
    }
  }

  private static boolean appendRelatedToModule(Element element, String classesUrl, String rootName, VirtualFile file, Module module) {
    for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
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
