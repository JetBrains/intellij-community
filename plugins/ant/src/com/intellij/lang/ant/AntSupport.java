package com.intellij.lang.ant;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.changes.AntChangeVisitor;
import com.intellij.lang.ant.validation.AntDuplicateImportedTargetsInspection;
import com.intellij.lang.ant.validation.AntDuplicateTargetsInspection;
import com.intellij.lang.ant.validation.AntMissingPropertiesFileInspection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AntSupport implements ApplicationComponent, InspectionToolProvider {

  private static LanguageFileType ourFileType = null;
  private static AntLanguage ourLanguage = null;
  private static AntChangeVisitor ourChangeVisitor = null;
  private static Map<AntFile, WeakHashMap<AntFile, Boolean>> ourFileDependencies;

  public AntSupport(FileTypeManager fileTypeManager, ActionManager actionManager) {
    fileTypeManager.getRegisteredFileTypes();
    ((CompositeLanguage)StdLanguages.XML).registerLanguageExtension(new AntLanguageExtension());

    final AnAction addAntBuildFile = actionManager.getAction("AddAntBuildFile");
    assert addAntBuildFile != null;
    final DefaultActionGroup group = (DefaultActionGroup)actionManager.getAction("J2EEViewPopupMenu");
    if (group != null) {
      group.add(addAntBuildFile, new Constraints(Anchor.AFTER, "ValidateXml"));
    }
    SelectWordUtil.registerSelectioner(new AntPropertyRefSelectioner());
  }

  public static synchronized AntLanguage getLanguage() {
    if (ourLanguage == null) {
      if (ourFileType == null) {
        ourFileType = new AntFileType();
      }
      ourLanguage = (AntLanguage)ourFileType.getLanguage();
    }
    return ourLanguage;
  }

  public static synchronized AntChangeVisitor getChangeVisitor() {
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

  public static synchronized List<AntFile> getImpotingFiles(final AntFile imported) {
    checkDependenciesCache();
    final WeakHashMap<AntFile, Boolean> files = ourFileDependencies.get(imported);
    if (files != null) {
      final int size = files.size();
      if (size > 0) {
        final List<AntFile> result = new ArrayList<AntFile>(size);
        int i = 0;
        for (final AntFile file : files.keySet()) {
          if (file != null) {
            result.add(file);
          }
        }
        return result;
      }
    }
    return Collections.emptyList();
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

  public static AntFile getAntFile ( PsiFile psiFile ) {
    if (psiFile instanceof AntFile) {
      return (AntFile)psiFile;
    }
    else {
      return (AntFile)psiFile.getViewProvider().getPsi(AntSupport.getLanguage());
    }
  }
}
