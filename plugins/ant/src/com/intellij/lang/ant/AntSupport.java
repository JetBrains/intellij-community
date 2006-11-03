package com.intellij.lang.ant;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.changes.AntChangeVisitor;
import com.intellij.lang.ant.validation.AntDuplicateImportedTargetsInspection;
import com.intellij.lang.ant.validation.AntDuplicateTargetsInspection;
import com.intellij.lang.ant.validation.AntMissingPropertiesFileInspection;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.WeakHashMap;

public class AntSupport implements ApplicationComponent, InspectionToolProvider {

  private static LanguageFileType ourFileType = null;
  private static AntLanguage ourLanguage = null;
  private static AntChangeVisitor ourChangeVisitor = null;
  private static Map<AntFile, WeakHashMap<AntFile, Boolean>> ourFileDependencies;

  public AntSupport(FileTypeManager fileTypeManager) {
    fileTypeManager.getRegisteredFileTypes();
    ((CompositeLanguage)StdLanguages.XML).registerLanguageExtension(new AntLanguageExtension());
  }

  public static AntLanguage getLanguage() {
    if (ourLanguage == null) {
      if (ourFileType == null) {
        ourFileType = new AntFileType();
      }
      ourLanguage = (AntLanguage)ourFileType.getLanguage();
    }
    return ourLanguage;
  }

  public static AntChangeVisitor getChangeVisitor() {
    if (ourChangeVisitor == null) {
      ourChangeVisitor = new AntChangeVisitor();
    }
    return ourChangeVisitor;
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "AntSupport";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static void markFileAsAntFile(final VirtualFile file, final FileViewProvider viewProvider, final boolean isAntFile) {
    Boolean oldValue = file.getUserData(AntLanguageExtension.ANT_FILE_SIGN);
    if (oldValue == null) {
      oldValue = false;
    }
    if (oldValue.booleanValue() != isAntFile) {
      file.putUserData(AntLanguageExtension.ANT_FILE_SIGN, (isAntFile) ? true : null);
      viewProvider.contentsSynchronized();
    }
  }

  public Class[] getInspectionClasses() {
    return new Class[]{AntDuplicateTargetsInspection.class, AntDuplicateImportedTargetsInspection.class,
      AntMissingPropertiesFileInspection.class};
  }

  //
  // Managing ant files dependencies via the <import> task.
  //

  public static synchronized AntFile[] getImpotingFiles(final AntFile imported) {
    checkDependenciesCache();
    final WeakHashMap<AntFile, Boolean> files = ourFileDependencies.get(imported);
    if (files != null) {
      final int size = files.size();
      if (size > 0) {
        final AntFile[] result = new AntFile[size];
        int i = 0;
        for (final AntFile file : files.keySet()) {
          result[i++] = file;
        }
        return result;
      }
    }
    return AntFile.NO_FILES;
  }

  public static synchronized void registerDependency(final AntFile importing, final AntFile imported) {
    checkDependenciesCache();
    final Map<AntFile, WeakHashMap<AntFile,Boolean>> dependencies = ourFileDependencies;
    WeakHashMap<AntFile, Boolean> files = dependencies.get(imported);
    if(files == null) {
      files = new WeakHashMap<AntFile, Boolean>();
      dependencies.put(imported, files);
    }
    files.put(importing, true);
  }

  private static void checkDependenciesCache() {
    if (ourFileDependencies == null) {
      ourFileDependencies = new WeakHashMap<AntFile, WeakHashMap<AntFile, Boolean>>();
    }
  }
}
