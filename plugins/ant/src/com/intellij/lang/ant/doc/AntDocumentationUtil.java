package com.intellij.lang.ant.doc;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

public class AntDocumentationUtil {
  @NonNls private static final String CORE_TASKS_FOLDER_NAME = "/CoreTasks/";
  @NonNls private static final String CORE_TYPES_FOLDER_NAME = "/CoreTypes/";
  @NonNls private static final String OPTIONAL_TASKS_FOLDER_NAME = "/OptionalTasks/";
  @NonNls private static final String OPTIONAL_TYPES_FOLDER_NAME = "/OptionalTypes/";

  private static String generateAntDocNew(final PsiElement originalElement) {

    PsiFile containingFile = originalElement.getContainingFile();
    AntConfiguration instance = AntConfiguration.getInstance(originalElement.getProject());

    for (AntBuildFile buildFile : instance.getBuildFiles()) {
      if (buildFile.getAntFile().equals(containingFile)) {
        final AntInstallation installation = AntBuildFileImpl.ANT_INSTALLATION.get(((AntBuildFileImpl)buildFile).getAllOptions());
        if (installation != null) {
          final String antHomeDir = AntInstallation.HOME_DIR.get(installation.getProperties());

          if (antHomeDir != null) {
            final @NonNls String path = antHomeDir + "/docs/manual";
            XmlTag tag = PsiTreeUtil.getParentOfType(originalElement, XmlTag.class);

            if (tag == null) return null;

            @NonNls final String helpFileShortName = tag.getName() + ".html";


            File file = new File(path);
            File helpFile = null;

            if (file.exists()) {
              File candidateHelpFile = new File(path + CORE_TASKS_FOLDER_NAME + helpFileShortName);
              if (candidateHelpFile.exists()) helpFile = candidateHelpFile;

              if (helpFile == null) {
                candidateHelpFile = new File(path + CORE_TYPES_FOLDER_NAME + helpFileShortName);
                if (candidateHelpFile.exists()) helpFile = candidateHelpFile;
              }

              if (helpFile == null) {
                candidateHelpFile = new File(path + OPTIONAL_TASKS_FOLDER_NAME + helpFileShortName);
                if (candidateHelpFile.exists()) helpFile = candidateHelpFile;
              }

              if (helpFile == null) {
                candidateHelpFile = new File(path + OPTIONAL_TYPES_FOLDER_NAME + helpFileShortName);
                if (candidateHelpFile.exists()) helpFile = candidateHelpFile;
              }
            }

            if (helpFile != null) {
              final File helpFile1 = helpFile;
              VirtualFile fileByIoFile = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
                public VirtualFile compute() {
                  return LocalFileSystem.getInstance().findFileByIoFile(helpFile1);
                }
              });

              if (fileByIoFile != null) {
                try {
                  return VfsUtil.loadText(fileByIoFile);
                }
                catch (IOException e) {
                  // ignore exception
                }
              }
            }
          }
        }
      }
    }
    return null;
  }
}
