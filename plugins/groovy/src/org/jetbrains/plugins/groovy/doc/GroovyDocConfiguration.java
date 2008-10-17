package org.jetbrains.plugins.groovy.doc;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

/**
 * @author Dmitry Krasilschikov
 */
@State(
    name = "GroovyDocSettings",
    storages = {
    @Storage(
        id = "groovydoc_config",
        file = "$APP_CONFIG$/groovydoc_config.xml"
    )}
)

public class GroovyDocConfiguration implements PersistentStateComponent<GroovyDocConfiguration> {
  public String OUTPUT_DIRECTORY;
  public String INPUT_DIRECTORY;
  public String WINDOW_TITLE;
  public String[] PACKAGES;

  public boolean OPTION_IS_USE;
  public boolean OPTION_IS_PRIVATE;
  public boolean OPEN_IN_BROWSER;

  public GroovyDocConfiguration() {
    OUTPUT_DIRECTORY = "";
    INPUT_DIRECTORY = "";
    WINDOW_TITLE = "";

    PACKAGES = new String[]{"**.*"};

    OPEN_IN_BROWSER = true;
    OPTION_IS_USE = true;
    OPTION_IS_PRIVATE = true;
  }

  
  //private GenerationOptions myGenerationOptions;

  //public static final class GenerationOptions {
  //  public final String[] myPackageFQName;
  //  public final PsiDirectory mySourceDirectory;
  //  private final String myDestDirectory;
  //
  //  public GenerationOptions(String[] packagesFQName, PsiDirectory sourceDirectory, String destDirectory) {
  //    myPackageFQName = packagesFQName;
  //    mySourceDirectory = sourceDirectory;
  //    myDestDirectory = destDirectory;
  //  }
  //}
  //
  //public void setGenerationOptions(GenerationOptions generationOptions) {
  //  myGenerationOptions = generationOptions;
  //}
  //
  //public void checkConfiguration() throws RuntimeConfigurationException {
  //  if (myGenerationOptions == null) {
  //    throw new RuntimeConfigurationError(GroovyDocBundle.message("groovydoc.settings.not.specified"));
  //  }
  //}

  public GroovyDocConfigurable createConfigurable(final DataContext dataContext) {
    return new GroovyDocConfigurable(this, dataContext);
  }


  public static boolean containsPackagePrefix(Module module, String packageFQName) {
    if (module == null) return false;
    final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        final String packagePrefix = sourceFolder.getPackagePrefix();
        final int prefixLength = packagePrefix.length();
        if (prefixLength > 0 && packageFQName.startsWith(packagePrefix)) {
          return true;
        }
      }
    }
    return false;
  }

  public GroovyDocConfiguration getState() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public void loadState(final GroovyDocConfiguration state) {
    //To change body of implemented methods use File | Settings | File Templates.
  }
}
