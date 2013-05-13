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
package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.eclipse.ConversionException;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.conversion.*;
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Vladislav.Kaznacheev
 */
public class EclipseClasspathStorageProvider implements ClasspathStorageProvider {
  public static final String DESCR = EclipseBundle.message("eclipse.classpath.storage.description");

  @Override
  @NonNls
  public String getID() {
    return JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID;
  }

  @Override
  @Nls
  public String getDescription() {
    return DESCR;
  }

  @Override
  public void assertCompatible(final ModuleRootModel model) throws ConfigurationException {
    final String moduleName = model.getModule().getName();
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryEntry = (LibraryOrderEntry)entry;
        if (libraryEntry.isModuleLevel()) {
          final Library library = libraryEntry.getLibrary();
          if (library == null ||
              libraryEntry.getRootUrls(OrderRootType.CLASSES).length != 1 ||
              library.isJarDirectory(library.getUrls(OrderRootType.CLASSES)[0])) {
            throw new ConfigurationException(
              "Library \'" +
              entry.getPresentableName() +
              "\' from module \'" +
              moduleName +
              "\' dependencies is incompatible with eclipse format which supports only one library content root");
          }
        }
      }
    }
    if (model.getContentRoots().length == 0) {
      throw new ConfigurationException("Module \'" + moduleName + "\' has no content roots thus is not compatible with eclipse format");
    }
    final String output = model.getModuleExtension(CompilerModuleExtension.class).getCompilerOutputUrl();
    final String contentRoot = getContentRoot(model);
    if (output == null ||
        !StringUtil.startsWith(VfsUtilCore.urlToPath(output), contentRoot) &&
        PathMacroManager.getInstance(model.getModule()).collapsePath(output).equals(output)) {
      throw new ConfigurationException("Module \'" +
                                       moduleName +
                                       "\' output path is incompatible with eclipse format which supports output under content root only.\nPlease make sure that \"Inherit project compile output path\" is not selected");
    }
  }

  @Override
  public void detach(Module module) {
    EclipseModuleManagerImpl.getInstance(module).setDocumentSet(null);
  }

  @Override
  public ClasspathConverter createConverter(Module module) {
    return new EclipseClasspathConverter(module);
  }

  @Override
  public String getContentRoot(ModuleRootModel model) {
    final VirtualFile contentRoot = EPathUtil.getContentRoot(model);
    if (contentRoot != null) return contentRoot.getPath();
    return model.getContentRoots()[0].getPath();
  }

  @Override
  public void modulePathChanged(Module module, String path) {
    final EclipseModuleManagerImpl moduleManager = EclipseModuleManagerImpl.getInstance(module);
    if (moduleManager != null) {
      moduleManager.setDocumentSet(null);
    }
  }

  public static void registerFiles(final CachedXmlDocumentSet fileCache,
                                   final Module module,
                                   final String moduleRoot,
                                   final String storageRoot) {
    fileCache.register(EclipseXml.CLASSPATH_FILE, storageRoot);
    fileCache.register(EclipseXml.PROJECT_FILE, storageRoot);
    fileCache.register(EclipseXml.PLUGIN_XML_FILE, storageRoot);
    fileCache.register(module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX, moduleRoot);
  }

  static CachedXmlDocumentSet getFileCache(final Module module) {
    final EclipseModuleManagerImpl moduleManager = EclipseModuleManagerImpl.getInstance(module);
    CachedXmlDocumentSet fileCache = moduleManager != null ? moduleManager.getDocumentSet() : null;
    if (fileCache == null) {
      fileCache = new CachedXmlDocumentSet(module.getProject());
      if (moduleManager != null) {
        moduleManager.setDocumentSet(fileCache);
      }
      registerFiles(fileCache, module, ClasspathStorage.getModuleDir(module), ClasspathStorage.getStorageRootFromOptions(module));
      fileCache.preload();
    }
    return fileCache;
  }

  @Override
  public void moduleRenamed(final Module module, String newName) {
    if (ClassPathStorageUtil.getStorageType(module).equals(JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID)) {
      try {
        final CachedXmlDocumentSet documentSet = getFileCache(module);


        final String oldEmlName = module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX;

        final String root = documentSet.getParent(oldEmlName);
        final File source = new File(root, oldEmlName);
        if (source.exists()) {
          final File target = new File(root, newName + EclipseXml.IDEA_SETTINGS_POSTFIX);
          FileUtil.rename(source, target);
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target);
        }
        final CachedXmlDocumentSet fileCache = getFileCache(module);
        DotProjectFileHelper.saveDotProjectFile(module, fileCache.getParent(EclipseXml.PROJECT_FILE));
        fileCache.delete(oldEmlName);
        fileCache.register(newName + EclipseXml.IDEA_SETTINGS_POSTFIX, ClasspathStorage.getModuleDir(module));
        fileCache.load(newName + EclipseXml.IDEA_SETTINGS_POSTFIX);
      }
      catch (IOException ignore) {
      }
      catch (JDOMException e) {

      }
    }
  }


  public static class EclipseClasspathConverter implements ClasspathConverter {

    private final Module module;
    private static final Logger LOG = Logger.getInstance("#" + EclipseClasspathConverter.class.getName());

    public EclipseClasspathConverter(final Module module) {
      this.module = module;
    }

    @Override
    public CachedXmlDocumentSet getFileSet() {
      return getFileCache(module);
    }

    @Override
    public Set<String> getClasspath(ModifiableRootModel model, final Element element) throws IOException, InvalidDataException {
      try {
        final HashSet<String> usedVariables = new HashSet<String>();
        final CachedXmlDocumentSet documentSet = getFileSet();
        final String path = documentSet.getParent(EclipseXml.PROJECT_FILE);
        LOG.assertTrue(documentSet.exists(EclipseXml.PROJECT_FILE));
        final EclipseClasspathReader classpathReader = new EclipseClasspathReader(path, module.getProject(), null);
        classpathReader.init(model);
        if (documentSet.exists(EclipseXml.CLASSPATH_FILE)) {
          classpathReader.readClasspath(model, new ArrayList<String>(), new ArrayList<String>(), usedVariables, new HashSet<String>(), null,
                                        documentSet.read(EclipseXml.CLASSPATH_FILE).getRootElement());
        }
        else {
          EclipseClasspathReader.setOutputUrl(model, path + "/bin");
        }
        final String eml = model.getModule().getName() + EclipseXml.IDEA_SETTINGS_POSTFIX;
        if (documentSet.exists(eml)) {
          IdeaSpecificSettings.readIDEASpecific(model, documentSet, eml);
        }
        else {
          model.getModuleExtension(CompilerModuleExtension.class).setExcludeOutput(false);
        }

        ((RootModelImpl)model).writeExternal(element);
        return usedVariables;
      }
      catch (ConversionException e) {
        throw new InvalidDataException(e);
      }
      catch (WriteExternalException e) {
        throw new InvalidDataException(e);
      }
      catch (JDOMException e) {
        throw new InvalidDataException(e);
      }
    }

    @Override
    public void setClasspath(final ModuleRootModel model) throws IOException, WriteExternalException {
      try {
        final Element classpathElement = new Element(EclipseXml.CLASSPATH_TAG);
        final EclipseClasspathWriter classpathWriter = new EclipseClasspathWriter(model);
        final CachedXmlDocumentSet fileSet = getFileSet();

        Element element;
        try {
          element = fileSet.read(EclipseXml.CLASSPATH_FILE).getRootElement();
        }
        catch (Exception e) {
          element = null;
        }

        if (element != null || model.getSourceRoots().length > 0 || model.getOrderEntries().length > 2) {
          classpathWriter.writeClasspath(classpathElement, element);
          fileSet.write(new Document(classpathElement), EclipseXml.CLASSPATH_FILE);
        }

        try {
          fileSet.read(EclipseXml.PROJECT_FILE);
        }
        catch (Exception e) {
          DotProjectFileHelper.saveDotProjectFile(module, fileSet.getParent(EclipseXml.PROJECT_FILE));
        }

        final Element ideaSpecific = new Element(IdeaXml.COMPONENT_TAG);
        final String emlFilename = model.getModule().getName() + EclipseXml.IDEA_SETTINGS_POSTFIX;
        if (IdeaSpecificSettings.writeIDEASpecificClasspath(ideaSpecific, model)) {
          fileSet.write(new Document(ideaSpecific), emlFilename);
        }
        else {
          fileSet.delete(emlFilename);
        }
      }
      catch (ConversionException e) {
        throw new WriteExternalException(e.getMessage());
      }
    }
  }
}
