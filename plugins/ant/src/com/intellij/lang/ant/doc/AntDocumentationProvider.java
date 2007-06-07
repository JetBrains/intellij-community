package com.intellij.lang.ant.doc;

import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.psi.*;
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
import java.util.List;

public class AntDocumentationProvider implements DocumentationProvider {
  
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    if (!(originalElement instanceof AntElement)) {
      return null;
    }
    
    final File helpFile = getHelpFile(originalElement);
    if (helpFile == null) {
      return null;
    }
    final VirtualFile fileByIoFile = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().findFileByIoFile(helpFile);
      }
    });

    if (fileByIoFile == null) {
      return null;
    }
    final StringBuilder builder = StringBuilderSpinAllocator.alloc(); 
    try {
      final AntElement antElement = (AntElement)originalElement;
      final String additionalDoc = generateDocForElement(antElement);
      if (additionalDoc != null) {
        builder.append(additionalDoc);
      }

      final String mainDoc = VfsUtil.loadText(fileByIoFile);
      if (mainDoc != null) {
        builder.append(mainDoc);
      }
      return builder.toString();
    }
    catch (IOException ignored) {
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
    return null;
  }

  @Nullable
  private static String generateDocForElement(final AntElement antElement) {
    if (antElement instanceof AntFilesProvider) {
      final List<File> list = ((AntFilesProvider)antElement).getFiles();
      if (list.size() == 0) {
        return null;
      }
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
    final AntInstallation installation = antFile.getAntInstallation();
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

    final XmlTag xmlTag = (XmlTag) antElement.getSourceElement();
    @NonNls final String helpFileShortName = xmlTag.getName()+ ".html";
    @NonNls File candidateHelpFile = null;
    
    if (antElement instanceof AntTask) {
      candidateHelpFile = new File(path + CORE_TASKS_FOLDER_NAME + helpFileShortName);
      if (candidateHelpFile.exists()) return candidateHelpFile;

      candidateHelpFile = new File(path + OPTIONAL_TASKS_FOLDER_NAME + helpFileShortName);
      if (candidateHelpFile.exists()) return candidateHelpFile;
    }

    candidateHelpFile = new File(path + CORE_TYPES_FOLDER_NAME + helpFileShortName);
    if (candidateHelpFile.exists()) return candidateHelpFile;

    candidateHelpFile = new File(path + OPTIONAL_TYPES_FOLDER_NAME + helpFileShortName);
    if (candidateHelpFile.exists()) return candidateHelpFile;
    
    if(antElement instanceof AntTarget || antElement instanceof AntProject) {
      candidateHelpFile = new File(path + "/using.html");
      if (candidateHelpFile.exists()) return candidateHelpFile;
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
