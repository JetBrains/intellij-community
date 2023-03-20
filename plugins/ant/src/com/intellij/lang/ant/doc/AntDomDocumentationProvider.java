// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.doc;

import com.intellij.lang.ant.AntFilesProvider;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.dom.AntDomElement;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.lang.ant.dom.AntDomTarget;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

final class AntDomDocumentationProvider implements DocumentationProvider {
  private static final Logger LOG = Logger.getInstance(AntDomDocumentationProvider.class);

  @Override
  public @Nls String generateDoc(PsiElement element, PsiElement originalElement) {
    final String mainDoc = getMainDocumentation(originalElement);
    final String additionalDoc = getAdditionalDocumentation(originalElement);
    if (mainDoc == null && additionalDoc == null) {
      return null;
    }
    @NlsSafe final StringBuilder builder = new StringBuilder();
    if (additionalDoc != null) {
      builder.append(additionalDoc);
    }
    if (mainDoc != null) {
      builder.append(mainDoc);
    }
    return builder.toString();
  }

  @Nullable
  private static String getMainDocumentation(PsiElement elem) {
    final VirtualFile helpFile = getHelpFile(elem);
    if (helpFile != null) {
      try {
        return VfsUtilCore.loadText(helpFile);
      }
      catch (IOException ignored) {
      }
    }
    return null;
  }

  @Nullable
  private static String getAdditionalDocumentation(PsiElement elem) {
    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(elem, XmlTag.class);
    if (xmlTag == null) {
      return null;
    }
    final AntDomElement antElement = AntSupport.getAntDomElement(xmlTag);
    if (antElement instanceof AntFilesProvider) {
      final List<File> list = ((AntFilesProvider)antElement).getFiles(new HashSet<>());
      if (list.size() > 0) {
        final @NonNls StringBuilder builder = new StringBuilder();
        final XmlTag tag = antElement.getXmlTag();
        if (tag != null) {
          builder.append("<b>");
          builder.append(tag.getName());
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
    }
    return null;
  }

  @Nullable
  private static VirtualFile getHelpFile(final PsiElement element) {
    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (xmlTag == null) {
      return null;
    }
    final AntDomElement antElement = AntSupport.getAntDomElement(xmlTag);
    if (antElement == null) {
      return null;
    }
    final AntDomProject antProject = antElement.getAntProject();
    if (antProject == null) {
      return null;
    }
    final AntInstallation installation = antProject.getAntInstallation();
    if (installation == null) {
      return null; // not configured properly and bundled installation missing
    }
    final String antHomeDir = AntInstallation.HOME_DIR.get(installation.getProperties());

    if (antHomeDir == null) {
      return null;
    }

    @NonNls String path = antHomeDir + "/docs/manual";
    String url;
    if (new File(path).exists()) {
      url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(path));
    }
    else {
      path = antHomeDir + "/docs.zip";
      if (new File(path).exists()) {
        url = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(path) + JarFileSystem.JAR_SEPARATOR + "docs/manual");
      }
      else {
        return null;
      }
    }

    final VirtualFile documentationRoot = VirtualFileManager.getInstance().findFileByUrl(url);
    if (documentationRoot == null) {
      return null;
    }

    return getHelpFile(antElement, documentationRoot);
  }

  public static final String[] DOC_FOLDER_NAMES = new String[] {
    "Tasks", "Types", "CoreTasks", "OptionalTasks", "CoreTypes", "OptionalTypes"
  };

  @Nullable
  private static VirtualFile getHelpFile(AntDomElement antElement, final VirtualFile documentationRoot) {
    final XmlTag xmlTag = antElement.getXmlTag();
    if (xmlTag == null) {
      return null;
    }
    @NonNls final String helpFileShortName = "/" + xmlTag.getName() + ".html";

    for (String folderName : DOC_FOLDER_NAMES) {
      final VirtualFile candidateHelpFile = documentationRoot.findFileByRelativePath(folderName + helpFileShortName);
      if (candidateHelpFile != null) {
        return candidateHelpFile;
      }
    }

    if(antElement instanceof AntDomTarget|| antElement instanceof AntDomProject) {
      final VirtualFile candidateHelpFile = documentationRoot.findFileByRelativePath("using.html");
      if (candidateHelpFile != null) {
        return candidateHelpFile;
      }
    }

    return null;
  }

  @Override
  @Nullable
  public @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {  // todo!
    if (element instanceof PomTargetPsiElement) {
      final PomTarget pomTarget = ((PomTargetPsiElement)element).getTarget();
      if (pomTarget instanceof DomTarget) {
        final DomElement domElement = ((DomTarget)pomTarget).getDomElement();
        if (domElement instanceof AntDomTarget antTarget) {
          final String description = antTarget.getDescription().getRawText();
          if (description != null && description.length() > 0) {
            final String targetName = antTarget.getName().getRawText();
            final StringBuilder builder = new StringBuilder();
            builder.append("Target");
            if (targetName != null) {
              builder.append(" \"").append(targetName).append("\"");
            }
            final XmlElement xmlElement = antTarget.getXmlElement();
            if (xmlElement != null) {
              final PsiFile containingFile = xmlElement.getContainingFile();
              if (containingFile != null) {
                final String fileName = containingFile.getName();
                builder.append(" [").append(fileName).append("]");
              }
            }
            @NlsSafe final String result = builder.append(" ").append(description).toString();
            return result;
          }
        }
      }
      else if (pomTarget instanceof DomChildrenDescription) {
        final DomChildrenDescription description = (DomChildrenDescription)pomTarget;
        Type type = null;
        try {
          type = description.getType();
        }
        catch (UnsupportedOperationException e) {
          LOG.info(e);
        }
        if (type instanceof Class && AntDomElement.class.isAssignableFrom(((Class)type))) {
          final String elemName = description.getName();
          if (elemName != null) {
            final AntDomElement.Role role = description.getUserData(AntDomElement.ROLE);
            final StringBuilder builder = new StringBuilder();
            if (role == AntDomElement.Role.TASK) {
              builder.append("Task ");
            }
            else if (role == AntDomElement.Role.DATA_TYPE) {
              builder.append("Data structure ");
            }
            builder.append(elemName);
            @NlsSafe final String result = builder.toString();
            return result;
          }
        }
      }
    }
    return null;
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    final VirtualFile helpFile = getHelpFile(originalElement);
    if (helpFile == null || !(helpFile.getFileSystem() instanceof LocalFileSystem)) {
      return null;
    }
    return Collections.singletonList(helpFile.getUrl());
  }
}
