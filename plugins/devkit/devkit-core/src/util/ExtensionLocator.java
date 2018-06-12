// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.util.JvmClassUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

public abstract class ExtensionLocator {
  @NotNull
  public abstract List<ExtensionCandidate> findCandidates();

  @NotNull
  public static ExtensionLocator byClass(@NotNull Project project, @NotNull JvmClass clazz) {
    return new ExtensionByClassLocator(project, clazz);
  }

  public static ExtensionLocator byPsiClass(@NotNull PsiClass psiClass) {
    return new ExtensionByPsiClassLocator(psiClass);
  }

  public static ExtensionLocator byExtensionPoint(@NotNull ExtensionPoint extensionPoint) {
    return new ExtensionByExtensionPointLocator(extensionPoint, null);
  }

  public static ExtensionLocator byExtensionPointAndId(@NotNull ExtensionPoint extensionPoint, @NotNull String extensionId) {
    return new ExtensionByExtensionPointLocator(extensionPoint, extensionId);
  }


  private static class ExtensionByClassLocator extends ExtensionLocator {
    private final Project myProject;
    private final JvmClass myClazz;

    ExtensionByClassLocator(@NotNull Project project, @NotNull JvmClass clazz) {
      myProject = project;
      myClazz = clazz;
    }

    @NotNull
    @Override
    public List<ExtensionCandidate> findCandidates() {
      return findCandidatesByClassName(JvmClassUtil.getJvmClassName(myClazz), myProject);
    }
  }

  private static class ExtensionByPsiClassLocator extends ExtensionLocator {
    private final PsiClass myPsiClass;

    ExtensionByPsiClassLocator(PsiClass psiClass) {
      myPsiClass = psiClass;
    }

    @NotNull
    public List<ExtensionCandidate> findCandidates() {
      return findCandidatesByClassName(ClassUtil.getJVMClassName(myPsiClass), myPsiClass.getProject());
    }
  }

  private static class ExtensionByExtensionPointLocator extends ExtensionLocator {
    private final ExtensionPoint myExtensionPoint;
    private final String myExtensionId;

    private ExtensionByExtensionPointLocator(@NotNull ExtensionPoint extensionPoint, @Nullable String extensionId) {
      myExtensionPoint = extensionPoint;
      myExtensionId = extensionId;
    }

    @NotNull
    @Override
    public List<ExtensionCandidate> findCandidates() {
      XmlTag epTag = myExtensionPoint.getXmlTag();
      if (epTag == null) {
        return Collections.emptyList();
      }

      // We must search for the last part of EP name, because for instance 'com.intellij.console.folding' extension
      // may be declared as <extensions defaultExtensionNs="com"><intellij.console.folding ...
      String epNameToSearch = StringUtil.substringAfterLast(myExtensionPoint.getEffectiveQualifiedName(), ".");

      List<ExtensionCandidate> result = new SmartList<>();
      processExtensionDeclarations(epNameToSearch, epTag.getProject(), false, (extension, tag) -> {
        ExtensionPoint ep = extension.getExtensionPoint();
        if (ep == null) return true;

        if (StringUtil.equals(ep.getEffectiveQualifiedName(), myExtensionPoint.getEffectiveQualifiedName())
            && (myExtensionId == null || myExtensionId.equals(extension.getId().getStringValue()))) {
          result.add(new ExtensionCandidate(SmartPointerManager.getInstance(tag.getProject()).createSmartPsiElementPointer(tag)));
          return myExtensionId == null; // stop after the first found candidate if ID is specified
        }
        return true;
      });
      return result;
    }
  }


  private static void processExtensionDeclarations(@Nullable String name,
                                                   @NotNull Project project,
                                                   boolean strictMatch,
                                                   @NotNull BiFunction<Extension, XmlTag, Boolean> callback) {
    if (name == null) return;
    GlobalSearchScope scope = PluginRelatedLocatorsUtils.getCandidatesScope(project);

    PsiSearchHelper.getInstance(project).processElementsWithWord((element, offsetInElement) -> {
      if (!(element instanceof XmlTag)) {
        return true;
      }
      PsiElement elementAtOffset = element.findElementAt(offsetInElement);
      if (elementAtOffset == null) {
        return true;
      }

      String foundText = elementAtOffset.getText();
      if (!strictMatch && !StringUtil.contains(foundText, name)) {
        return true;
      }
      if (strictMatch && !StringUtil.equals(foundText, name)) {
        return true;
      }

      XmlTag tag = (XmlTag)element;
      DomElement dom = DomUtil.getDomElement(tag);
      if (!(dom instanceof Extension)) {
        return true;
      }

      return callback.apply((Extension)dom, tag);
    }, scope, name, UsageSearchContext.IN_FOREIGN_LANGUAGES, true);
  }

  private static List<ExtensionCandidate> findCandidatesByClassName(@Nullable String jvmClassName, @NotNull Project project) {
    List<ExtensionCandidate> result = new SmartList<>();
    processExtensionDeclarations(jvmClassName, project, true, (extension, tag) -> {
      if (extension.getExtensionPoint() != null) {
        result.add(new ExtensionCandidate(SmartPointerManager.getInstance(tag.getProject()).createSmartPsiElementPointer(tag)));
      }
      return true; // continue processing
    });
    return result;
  }
}
