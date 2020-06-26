// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.Query;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.inspections.quickfix.PluginDescriptorChooser;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.*;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class InspectionDescriptionInfo {
  private static final Logger LOG = Logger.getInstance(InspectionDescriptionInfo.class);

  private final String myFilename;
  private final PsiMethod myMethod;
  private final PsiFile myDescriptionFile;
  private final boolean myShortNameInXml;

  private InspectionDescriptionInfo(String filename, @Nullable PsiMethod method,
                                    @Nullable PsiFile descriptionFile, boolean shortNameInXml) {
    myFilename = filename;
    myMethod = method;
    myDescriptionFile = descriptionFile;
    myShortNameInXml = shortNameInXml;
  }

  public static InspectionDescriptionInfo create(Module module, PsiClass psiClass) {
    PsiMethod getShortNameMethod = PsiUtil.findNearestMethod("getShortName", psiClass);
    if (getShortNameMethod != null &&
        Objects.requireNonNull(getShortNameMethod.getContainingClass()).hasModifierProperty(PsiModifier.ABSTRACT)) {
      getShortNameMethod = null;
    }

    boolean shortNameInXml;
    String filename = null;
    if (getShortNameMethod == null) {
      shortNameInXml = true;
      String className = psiClass.getQualifiedName();
      if (className != null) {
        Extension extension = findExtension(module, psiClass);
        if (extension != null) {
          filename = extension.getXmlTag().getAttributeValue("shortName");
        }
      }
    }
    else {
      shortNameInXml = false;
      filename = getReturnedLiteral(getShortNameMethod, psiClass);
    }

    if (filename == null) {
      final String className = psiClass.getName();
      LOG.assertTrue(className != null, psiClass);
      filename = InspectionProfileEntry.getShortName(className);
    }

    PsiFile descriptionFile = resolveInspectionDescriptionFile(module, filename);
    return new InspectionDescriptionInfo(filename, getShortNameMethod, descriptionFile, shortNameInXml);
  }

  @Nullable
  public static Extension findExtension(Module module, PsiClass psiClass) {
    return CachedValuesManager.getCachedValue(psiClass, () -> {
      Extension extension = doFindExtension(module, psiClass);
      return CachedValueProvider.Result
        .create(extension, extension == null ? PsiModificationTracker.MODIFICATION_COUNT : extension.getXmlTag());
    });
  }

  @Nullable
  private static Extension doFindExtension(Module module, PsiClass psiClass) {
    // Try search in narrow scopes first
    Project project = module.getProject();
    Set<DomFileElement<IdeaPlugin>> processed = new HashSet<>();
    for (GlobalSearchScope scope : DescriptionCheckerUtil.searchScopes(module)) {
      List<DomFileElement<IdeaPlugin>> origElements = DomService.getInstance().getFileElements(IdeaPlugin.class, project, scope);
      origElements.removeAll(processed);
      List<DomFileElement<IdeaPlugin>> elements = PluginDescriptorChooser.findAppropriateIntelliJModule(module.getName(), origElements);

      Query<PsiReference> query =
        ReferencesSearch.search(psiClass, new LocalSearchScope(elements.stream().map(DomFileElement::getFile).toArray(PsiElement[]::new)));

      Ref<Extension> result = Ref.create(null);
      query.forEach(ref -> {
        PsiElement element = ref.getElement();
        if (element instanceof XmlAttributeValue) {
          PsiElement parent = element.getParent();
          if (parent instanceof XmlAttribute && "implementationClass".equals(((XmlAttribute)parent).getName())) {
            DomElement domElement = DomUtil.getDomElement(parent.getParent());
            if (domElement instanceof Extension) {
              Extension extension = (Extension)domElement;
              ExtensionPoint extensionPoint = extension.getExtensionPoint();
              if (extensionPoint != null &&
                  InheritanceUtil.isInheritor(extensionPoint.getBeanClass().getValue(), InspectionEP.class.getName())) {
                result.set(extension);
                return false;
              }
            }
          }
        }
        return true;
      });
      Extension extension = result.get();
      if (extension != null) return extension;

      processed.addAll(origElements);
    }
    return null;
  }

  @Nullable
  private static PsiFile resolveInspectionDescriptionFile(Module module, @Nullable String filename) {
    if (filename == null) return null;

    String nameWithSuffix = filename + ".html";
    return DescriptionCheckerUtil.allDescriptionDirs(module, DescriptionType.INSPECTION)
      .map(directory -> directory.findFile(nameWithSuffix)).nonNull().findFirst().orElse(null);
  }

  public boolean isValid() {
    return myFilename != null;
  }

  public String getFilename() {
    assert isValid();
    return myFilename;
  }

  @Nullable
  public PsiMethod getShortNameMethod() {
    return myMethod;
  }

  @Nullable
  public PsiFile getDescriptionFile() {
    return myDescriptionFile;
  }

  public boolean hasDescriptionFile() {
    return getDescriptionFile() != null;
  }

  public boolean isShortNameInXml() {
    return myShortNameInXml;
  }

  @Nullable
  private static String getReturnedLiteral(PsiMethod method, PsiClass cls) {
    final UExpression expression = PsiUtil.getReturnedExpression(method);
    if (expression == null) return null;

    if (expression instanceof UReferenceExpression) {
      final String methodName = ((UReferenceExpression)expression).getResolvedName();
      if ("getSimpleName".equals(methodName)) {
        return cls.getName();
      }
    }

    return UastUtils.evaluateString(expression);
  }
}
