// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.lang.properties.BundleNameEvaluator;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.lang.properties.references.PropertyReferenceBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.include.FileIncludeManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.NullableFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Actions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorI18nUtil;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.Collections;
import java.util.List;

class PropertyKeyReferenceProvider extends PsiReferenceProvider {

  private final boolean myTagMode;

  @Nullable private final String myFallbackKeyName;
  @Nullable private final String myFallbackBundleName;

  @Nullable private final NullableFunction<? super XmlTag, String> myBundleNameFunction;

  PropertyKeyReferenceProvider(boolean tagMode, @NonNls @Nullable String fallbackKeyName, @Nullable String fallbackBundleName) {
    myTagMode = tagMode;
    myFallbackKeyName = fallbackKeyName;
    myFallbackBundleName = fallbackBundleName;
    myBundleNameFunction = null;
  }

  PropertyKeyReferenceProvider(@NotNull NullableFunction<? super XmlTag, String> bundleNameFunction) {
    myBundleNameFunction = bundleNameFunction;
    myTagMode = false;
    myFallbackKeyName = null;
    myFallbackBundleName = null;
  }

  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return PropertyReferenceBase.isPropertyPsi(target);
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    if (myTagMode && element instanceof XmlTag) {
      return getTagReferences(((XmlTag)element));
    }

    if (element instanceof XmlAttributeValue) {
      final XmlAttribute xmlAttribute = (XmlAttribute)element.getParent();
      if (element.getTextLength() < 2) {
        return PsiReference.EMPTY_ARRAY;
      }

      final XmlTag tag = xmlAttribute.getParent();
      String value = null;
      String bundle = myBundleNameFunction == null ? tag.getAttributeValue("bundle") : myBundleNameFunction.fun(tag);
      if ("key".equals(xmlAttribute.getName())) {
        value = xmlAttribute.getValue();
      }
      else if (xmlAttribute.getName().equals(myFallbackKeyName)) {
        value = xmlAttribute.getValue();
        final String groupBundle = tag.getAttributeValue(myFallbackBundleName);
        if (groupBundle != null) {
          bundle = groupBundle;
        }
      }

      if (value != null) {
        return new PsiReference[]{new MyPropertyReference(getFinalKeyValue(value), element, bundle, getFallbackBundleName())};
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @Nullable
  protected String getFallbackBundleName() {
    return null;
  }

  @NotNull
  protected String getFinalKeyValue(String keyValue) {
    return keyValue;
  }

  private PsiReference[] getTagReferences(XmlTag element) {
    final XmlTag parent = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (parent == null) return PsiReference.EMPTY_ARRAY;
    final XmlTag bundleNameTag = parent.findFirstSubTag(myFallbackBundleName);
    String bundleName = bundleNameTag != null ? bundleNameTag.getValue().getTrimmedText() : null;
    return new PsiReference[]{new MyPropertyReference(element.getValue().getText(), element, bundleName, getFallbackBundleName())};
  }


  private static final class MyPropertyReference extends PropertyReference {

    @Nullable
    private final String myBundleName;
    @Nullable
    private final String myFallbackBundleName;

    private MyPropertyReference(String value, PsiElement psiElement, @Nullable String bundleName, @Nullable String fallbackBundleName) {
      super(value, psiElement, bundleName, false);
      myBundleName = bundleName;
      myFallbackBundleName = fallbackBundleName;
    }

    @Override
    protected List<PropertiesFile> getPropertiesFiles() {
      return retrievePropertyFilesByBundleName(myBundleName, getElement());
    }

    @Override
    protected List<PropertiesFile> retrievePropertyFilesByBundleName(String bundleName, PsiElement element) {
      PsiElement psiElement = CompletionUtil.getOriginalOrSelf(element);
      final String bundleNameToUse =
        ObjectUtils.chooseNotNull(bundleName == null ? getPluginResourceBundle(psiElement) : bundleName,
                                  myFallbackBundleName);
      if (bundleNameToUse == null) {
        return Collections.emptyList();
      }

      final Project project = psiElement.getProject();
      final PropertiesReferenceManager propertiesReferenceManager = PropertiesReferenceManager.getInstance(project);

      final Module module = ModuleUtilCore.findModuleForPsiElement(psiElement);
      if (module != null) {
        List<PropertiesFile> propertiesFiles = propertiesReferenceManager.findPropertiesFiles(module, bundleNameToUse);
        if (!propertiesFiles.isEmpty()) {
          return propertiesFiles;
        }
      }

      return propertiesReferenceManager.findPropertiesFiles(psiElement.getResolveScope(),
                                                            bundleNameToUse, BundleNameEvaluator.DEFAULT);
    }

    @Nullable
    private static String getPluginResourceBundle(PsiElement element) {
      final DomElement domElement = DomUtil.getDomElement(element);
      if (domElement == null) return null;
      final DomElement rootElement = DomUtil.getFileElement(domElement).getRootElement();
      if (!(rootElement instanceof IdeaPlugin)) return null;

      IdeaPlugin plugin = (IdeaPlugin)rootElement;
      final String resourceBundle = plugin.getResourceBundle().getStringValue();
      if (StringUtil.isNotEmpty(resourceBundle)) {
        return resourceBundle;
      }

      final Actions actions = DomUtil.getParentOfType(domElement, Actions.class, true);
      if (DescriptorI18nUtil.canFallbackToCoreActionsBundle(actions)) {
        return DescriptorI18nUtil.CORE_ACTIONS_BUNDLE;
      }

      // for single xi:include, use including descriptor's <resource-bundle>
      final IdeaPlugin includingDescriptor = getSingleIncludingDescriptor(element);
      return includingDescriptor == null ? null : includingDescriptor.getResourceBundle().getStringValue();
    }

    @Nullable
    private static IdeaPlugin getSingleIncludingDescriptor(PsiElement element) {
      final VirtualFile[] includingFiles =
        FileIncludeManager.getManager(element.getProject()).getIncludingFiles(element.getContainingFile().getVirtualFile(), false);
      if (includingFiles.length != 1) return null;

      final PsiFile psiFile = element.getManager().findFile(includingFiles[0]);
      if (!(psiFile instanceof XmlFile)) return null;
      return DescriptorUtil.getIdeaPlugin(((XmlFile)psiFile));
    }
  }
}