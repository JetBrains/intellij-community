package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider;
import com.intellij.openapi.roots.impl.storage.FileSet;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.*;
import org.jetbrains.idea.eclipse.action.EclipseBundle;
import org.jetbrains.idea.eclipse.util.JDOM;
import org.jetbrains.idea.eclipse.util.XmlDocumentSet;

import java.io.IOException;

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
    return model.getContentEntries().length == 1;
  }

  public static EclipseToIdeaConverter.LibraryResolver createLibraryResolver(final Project project) {
    final LibraryTable globalLibraryTable =
      LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(LibraryTablesRegistrar.APPLICATION_LEVEL, project);
    final LibraryTable projectLibraryTable =
      LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(LibraryTablesRegistrar.PROJECT_LEVEL, project);

    return new EclipseToIdeaConverter.LibraryResolver() {
      public boolean isGlobal(final String name) {
        return globalLibraryTable.getLibraryByName(name) != null;
      }

      public boolean isProject(final String name) {
        return projectLibraryTable.getLibraryByName(name) != null;
      }
    };
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
        JDOM.getOrCreateChild(document.getRootElement(), EclipseXml.NAME_TAG).setText(module.getName());
        documentSet.write(document, EclipseXml.PROJECT_FILE);
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

    public FileSet getFileSet() {
      CachedXmlDocumentSet fileCache = EclipseModuleManager.getInstance(module).getDocumentSet();
      return fileCache != null ? fileCache : getFileCache(module);
    }

    public IdeaModuleModel getClasspath(final Element element) throws IOException, InvalidDataException {
      try {
        return EclipseToIdeaConverter.convert(element, true, ClasspathStorage.getModuleDir(module), module.getName(), ClasspathStorage.getStorageRootMap(module.getProject(), module),
                                       getDocumentSet(module), EclipseToIdeaConverter.Options.defValue, EclipseProjectReader.Options.defValue,
                                       createLibraryResolver(module.getProject()));
      }
      catch (ConversionException e) {
        throw new InvalidDataException(e);
      }
    }

    public void setClasspath(final Element element) throws IOException, WriteExternalException {
      try {
        IdeaToEclipseConverter
          .convert(ClasspathStorage.getModuleDir(module), module.getName(), element, true, ClasspathStorage.getStorageRootMap(module.getProject(), null),
                   getDocumentSet(module));
      }
      catch (ConversionException e) {
        throw new WriteExternalException(e.getMessage());
      }
    }
  }
}
