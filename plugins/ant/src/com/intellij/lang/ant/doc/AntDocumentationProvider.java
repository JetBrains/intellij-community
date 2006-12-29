package com.intellij.lang.ant.doc;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class AntDocumentationProvider implements DocumentationProvider {
  public String generateDoc(PsiElement element, PsiElement originalElement) {

    if (!(originalElement instanceof AntElement)) {
      return null;
    }

    AntElement antElement = (AntElement)originalElement;

    PsiFile containingFile = antElement.getContainingFile();
    AntConfiguration instance = AntConfiguration.getInstance(antElement.getProject());

    for (AntBuildFile buildFile : instance.getBuildFiles()) {
      if (buildFile.getAntFile().equals(containingFile)) {
        final AntInstallation installation = AntBuildFileImpl.ANT_INSTALLATION.get(((AntBuildFileImpl)buildFile).getAllOptions());
        if (installation != null) {
          final String antHomeDir = AntInstallation.HOME_DIR.get(installation.getProperties());

          if (antHomeDir != null) {
            final @NonNls String path = antHomeDir + "/docs/manual";

            File helpFile = new File(path).exists() ? getHelpFile(antElement, path) : null;

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


  @Nullable
  static private String getTagName(final AntElement element) {
    final XmlElement xmlElement = element.getSourceElement();
    XmlTag tag = (xmlElement instanceof XmlTag) ? (XmlTag)xmlElement : PsiTreeUtil.getParentOfType(xmlElement, XmlTag.class);
    return tag == null ? null : tag.getName();

  }

  @NonNls private static final String CORE_TASKS_FOLDER_NAME = "/CoreTasks/";
  @NonNls private static final String CORE_TYPES_FOLDER_NAME = "/CoreTypes/";
  @NonNls private static final String OPTIONAL_TYPES_FOLDER_NAME = "/OptionalTypes/";
  @NonNls private static final String OPTIONAL_TASKS_FOLDER_NAME = "/OptionalTasks/";

  @Nullable
  private static File getHelpFile(AntElement antElement, final String path) {

    if (antElement instanceof AntTask) {

      final XmlTag xmlTag = (XmlTag) antElement.getSourceElement();
      @NonNls final String helpFileShortName = xmlTag.getName()+ ".html";

      File candidateHelpFile = new File(path + CORE_TASKS_FOLDER_NAME + helpFileShortName);
      if (candidateHelpFile.exists()) return candidateHelpFile;

      candidateHelpFile = new File(path + CORE_TYPES_FOLDER_NAME + helpFileShortName);
      if (candidateHelpFile.exists()) return candidateHelpFile;

      candidateHelpFile = new File(path + OPTIONAL_TASKS_FOLDER_NAME + helpFileShortName);
      if (candidateHelpFile.exists()) return candidateHelpFile;

      candidateHelpFile = new File(path + OPTIONAL_TYPES_FOLDER_NAME + helpFileShortName);
      if (candidateHelpFile.exists()) return candidateHelpFile;
    }
    
    if(antElement instanceof AntTarget || antElement instanceof AntProject) {
      @NonNls File candidateHelpFile = new File(path + "/using.html");
      if (candidateHelpFile.exists()) return candidateHelpFile;
    }

    return null;
  }

  @Nullable
  public String getQuickNavigateInfo(PsiElement element) {
    return null;
  }

  public String getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
