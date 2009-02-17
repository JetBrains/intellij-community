package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.ConversionException;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.action.EclipseBundle;
import org.jetbrains.idea.eclipse.reader.EclipseClasspathReader;
import org.jetbrains.idea.eclipse.util.XmlDocumentSet;
import org.jetbrains.idea.eclipse.writer.EclipseClasspathWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Vladislav.Kaznacheev
 */
public class EclipseClasspathStorageProvider implements ClasspathStorageProvider {
  @NonNls public static final String ID = "eclipse";
  public static final String DESCR = EclipseBundle.message("eclipse.classpath.storage.description");

  @NonNls
  public String getID() {
    return ID;
  }

  @Nls
  public String getDescription() {
    return DESCR;
  }

  public void assertCompatible(final ModifiableRootModel model) throws ConfigurationException {
    final String incompatibleLibrary = hasIncompatibleLibrary(model);
    if (incompatibleLibrary != null) {
      throw new ConfigurationException(
        "Library \'" + incompatibleLibrary + "\' is incompatible with eclipse format: has too many classes roots");
    }
    if (!isCompatible(model)) {
      throw new ConfigurationException(EclipseBundle.message("eclipse.export.too.many.content.roots", model.getModule().getName()));
    }
  }

  public void detach(Module module) {
    EclipseModuleManager.getInstance(module).setDocumentSet(null);
  }

  public ClasspathConverter createConverter(Module module) {
    return new EclipseClasspathConverter(module);
  }

  public static boolean isCompatible(final ModuleRootModel model) {
    return model.getContentEntries().length <= 1;
  }

  @Nullable
  public static String hasIncompatibleLibrary(ModuleRootModel model) {
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).isModuleLevel()) {
        final Library library = ((LibraryOrderEntry)entry).getLibrary();
        if (library == null || entry.getUrls(OrderRootType.CLASSES).length != 1 || library.isJarDirectory(library.getUrls(OrderRootType.CLASSES)[0])) {
          return entry.getPresentableName();
        }
      }
    }
    return null;
  }

  static void registerFiles(final CachedFileSet fileCache, final Module module, final String moduleRoot, final String storageRoot) {
    fileCache.register(EclipseXml.CLASSPATH_FILE, storageRoot);
    fileCache.register(EclipseXml.PROJECT_FILE, storageRoot);
    fileCache.register(EclipseXml.PLUGIN_XML_FILE, storageRoot);
    fileCache.register(module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX, moduleRoot);
  }

  static XmlDocumentSet getDocumentSet(final Module module) {
    final CachedXmlDocumentSet fileCache = getFileCache(module);

    return new XmlDocumentSet() {

      public boolean exists(final String name) {
        return fileCache.exists(name);
      }

      public String getParent(String name) {
        return fileCache.getParent(name);
      }

      public Document read(String name) throws IOException, JDOMException {
        return fileCache.read(name);
      }

      public void write(Document document, String name) throws IOException {
        fileCache.write(document, name);
      }

      public void delete(String name) {
        fileCache.delete(name);
      }
    };
  }

  static CachedXmlDocumentSet getFileCache(final Module module) {
    CachedXmlDocumentSet fileCache = EclipseModuleManager.getInstance(module).getDocumentSet();
    if (fileCache == null) {
      fileCache = new CachedXmlDocumentSet();
      EclipseModuleManager.getInstance(module).setDocumentSet(fileCache);
      registerFiles(fileCache, module, ClasspathStorage.getModuleDir(module), ClasspathStorage.getStorageRootFromOptions(module));
      fileCache.preload();
    }
    return fileCache;
  }

  public static void moduleRenamed(final Module module) {
    if (ClasspathStorage.getStorageType(module).equals(ID)) {
      try {
        final XmlDocumentSet documentSet = getDocumentSet(module);
        final Document document = documentSet.read(EclipseXml.PROJECT_FILE);
        final Element projectNameElement = document.getRootElement().getChild(EclipseXml.NAME_TAG);
        if (projectNameElement == null) return;
        final String oldModuleName = projectNameElement.getText();
        projectNameElement.setText(module.getName());
        documentSet.write(document, EclipseXml.PROJECT_FILE);
        final String oldEmlName = oldModuleName + EclipseXml.IDEA_SETTINGS_POSTFIX;
        if (documentSet.exists(oldEmlName)) {
          final String root = documentSet.getParent(oldEmlName);
          final File source = new File(root, oldEmlName);
          final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(source);
          VfsUtil.copyFile(null, virtualFile, virtualFile.getParent(), module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX);
          virtualFile.delete(null);

          final CachedXmlDocumentSet fileCache = getFileCache(module);
          fileCache.delete(oldEmlName);
          registerFiles(fileCache, module, ClasspathStorage.getModuleDir(module), ClasspathStorage.getStorageRootFromOptions(module));
          fileCache.preload();
        }
      }
      catch (IOException ignore) {
      }
      catch (JDOMException ignore) {
      }
    }
  }

  @Nullable
  public static String getEclipseProjectName(final Module module) {
    if (ClasspathStorage.getStorageType(module).equals(ID)) {
      try {
        return getDocumentSet(module).read(EclipseXml.PROJECT_FILE).getRootElement().getChildText(EclipseXml.NAME_TAG);
      }
      catch (IOException ignore) {
      }
      catch (JDOMException ignore) {
      }
    }
    return null;
  }

  public static class EclipseClasspathConverter implements ClasspathConverter {

    private final Module module;

    public EclipseClasspathConverter(final Module module) {
      this.module = module;
    }

    public CachedXmlDocumentSet getFileSet() {
      CachedXmlDocumentSet fileCache = EclipseModuleManager.getInstance(module).getDocumentSet();
      return fileCache != null ? fileCache : getFileCache(module);
    }

    public Set<String> getClasspath(ModifiableRootModel model, final Element element) throws IOException, InvalidDataException {
      try {
        final HashSet<String> usedVariables = new HashSet<String>();
        final CachedXmlDocumentSet documentSet = getFileSet();
        final VirtualFile vFile = documentSet.getVFile(EclipseXml.PROJECT_FILE);
        final String path = vFile.getParent().getPath();
        final ContentEntry contentEntry = model.addContentEntry(VfsUtil.pathToUrl(path));
        if (documentSet.exists(EclipseXml.CLASSPATH_FILE)) {
          final String eml = model.getModule().getName() + EclipseXml.IDEA_SETTINGS_POSTFIX;
          if (documentSet.exists(eml)) {
            EclipseClasspathReader.readIDEASpecific(documentSet.read(eml).getRootElement(), model);
          }

          final EclipseClasspathReader classpathReader = new EclipseClasspathReader(path, contentEntry, module.getProject());
          classpathReader.readClasspath(model, new ArrayList<String>(), new ArrayList<String>(), usedVariables, new HashSet<String>(), null,
                                        documentSet.read(EclipseXml.CLASSPATH_FILE).getRootElement());

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

    public void setClasspath(final ModifiableRootModel model) throws IOException, WriteExternalException {
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
        
        if (element != null || model.getSourceRoots().length > 0) {
          classpathWriter.writeClasspath(classpathElement, element);
          fileSet.write(new Document(classpathElement), EclipseXml.CLASSPATH_FILE);
        }

        final Element ideaSpecific = new Element(IdeaXml.COMPONENT_TAG);
        final String emlFilename = model.getModule().getName() + EclipseXml.IDEA_SETTINGS_POSTFIX;
        if (classpathWriter.writeIDEASpecificClasspath(ideaSpecific)) {
          fileSet.write(new Document(ideaSpecific), emlFilename);
        } else {
          fileSet.delete(emlFilename);
        }
      }
      catch (ConversionException e) {
        throw new WriteExternalException(e.getMessage());
      }
    }
  }
}
