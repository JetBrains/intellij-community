package com.intellij.lang.ant.doc;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntFilesProvider;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.impl.reference.AntElementCompletionWrapper;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

public class AntDocumentationProvider implements DocumentationProvider {
  
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    final String mainDoc = getMainDocumentation(element);
    final String additionalDoc = getAdditionalDocumentation(element);
    if (mainDoc == null && additionalDoc == null) {
      return null;
    }
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      if (additionalDoc != null) {
        builder.append(additionalDoc);
      }
      if (mainDoc != null) {
        builder.append(mainDoc);
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @Nullable
  private static String getMainDocumentation(PsiElement elem) {
    if (!(elem instanceof AntElement)) {
      return null;
    }
    
    final File helpFile = getHelpFile(elem);
    if (helpFile == null) {
      return null;
    }
    final VirtualFile fileByIoFile = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Nullable public VirtualFile compute() {
        return LocalFileSystem.getInstance().findFileByIoFile(helpFile);
      }
    });

    if (fileByIoFile == null) {
      return null;
    }
    try {
      return VfsUtil.loadText(fileByIoFile);
    }
    catch (IOException ignored) {
    }
    return null;
  }
  
  @Nullable
  private static String getAdditionalDocumentation(PsiElement elem) {
    if (!(elem instanceof AntElement)) {
      return null;
    }
    final AntElement antElement = (AntElement)elem;
    if (antElement instanceof AntFilesProvider) {
      final List<File> list = ((AntFilesProvider)antElement).getFiles(new HashSet<AntFilesProvider>());
      if (list.size() > 0) {
        final @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
        try {
          final XmlElement srcElement = antElement.getSourceElement();
          if (srcElement instanceof XmlTag) {
            builder.append("<b>");
            builder.append(((XmlTag)srcElement).getName());
            builder.append(":</b>");
          }
          for (File file : list) {
            if (builder.length() > 0) {
              builder.append("<br>");
            }
            builder.append(file.getPath());
          }
          return builder.toString();
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }
      }
    }
    return null;
  }
  
  @Nullable
  private static File getHelpFile(final PsiElement element) {
    if (!(element instanceof AntElement)) {
      return null;
    }
    final AntElement antElement = (AntElement)element;
    final AntFile antFile = antElement.getAntFile();
    if (antFile == null) {
      return null;
    }
    final AntFile originalFile = (AntFile)antFile.getOriginalFile();
    final AntInstallation installation = originalFile != null? originalFile.getAntInstallation() : antFile.getAntInstallation();
    if (installation == null) {
      return null; // not configured properly and bundled installation missing
    }
    final String antHomeDir = AntInstallation.HOME_DIR.get(installation.getProperties());

    if (antHomeDir == null) {
      return null;
    }
    
    final @NonNls String path = antHomeDir + "/docs/manual";

    return new File(path).exists() ? getHelpFile(antElement, path) : null;
  }
  
  @NonNls private static final String CORE_TASKS_FOLDER_NAME = "/CoreTasks/";
  @NonNls private static final String CORE_TYPES_FOLDER_NAME = "/CoreTypes/";
  @NonNls private static final String OPTIONAL_TYPES_FOLDER_NAME = "/OptionalTypes/";
  @NonNls private static final String OPTIONAL_TASKS_FOLDER_NAME = "/OptionalTasks/";

  @Nullable
  private static File getHelpFile(AntElement antElement, final String path) {
    @NonNls final String helpFileShortName;
    if (antElement instanceof AntElementCompletionWrapper) {
      final String name = ((AntElementCompletionWrapper)antElement).getName();
      if (name == null) {
        return null; // should not happen for valid element
      }
      helpFileShortName = name.trim()+ ".html";
    }
    else {
      final XmlTag xmlTag = (XmlTag) antElement.getSourceElement();
      helpFileShortName = xmlTag.getName()+ ".html";
    }

    @NonNls File candidateHelpFile = new File(path + CORE_TASKS_FOLDER_NAME + helpFileShortName);
    if (candidateHelpFile.exists()) {
      return candidateHelpFile;
    }

    candidateHelpFile = new File(path + OPTIONAL_TASKS_FOLDER_NAME + helpFileShortName);
    if (candidateHelpFile.exists()) {
      return candidateHelpFile;
    }

    candidateHelpFile = new File(path + CORE_TYPES_FOLDER_NAME + helpFileShortName);
    if (candidateHelpFile.exists()) {
      return candidateHelpFile;
    }

    candidateHelpFile = new File(path + OPTIONAL_TYPES_FOLDER_NAME + helpFileShortName);
    if (candidateHelpFile.exists()) {
      return candidateHelpFile;
    }

    if(AntElementRole.TARGET_ROLE.equals(antElement.getRole()) || AntElementRole.PROJECT_ROLE.equals(antElement.getRole())) {
      candidateHelpFile = new File(path + "/using.html");
      if (candidateHelpFile.exists()) {
        return candidateHelpFile;
      }
    }

    return null;
  }

  @Nullable
  public String getQuickNavigateInfo(PsiElement element) {
    if (element instanceof AntTarget) {
      final String description = ((AntTarget)element).getDescription();
      if (description != null && description.length() > 0) {
        return description;
      }
    }
    return null;
  }

  public String getUrlFor(PsiElement element, PsiElement originalElement) {
    final File helpFile = getHelpFile(originalElement);
    if (helpFile == null) {
      return null;
    }
    return VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(helpFile.getPath()));
  }

  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }
}
