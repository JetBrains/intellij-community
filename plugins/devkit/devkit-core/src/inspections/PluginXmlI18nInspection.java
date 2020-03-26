// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.ResourceBundleManager;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.NameUtilCore;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PluginXmlI18nInspection extends DevKitPluginXmlInspectionBase {

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (element instanceof ActionOrGroup) {
      highlightAction(holder, (ActionOrGroup)element);
    }
    else if (element instanceof Extension) {
      ExtensionPoint extensionPoint = ((Extension)element).getExtensionPoint();
      if (extensionPoint != null) {

        String epName = extensionPoint.getEffectiveQualifiedName();
        if (LocalInspectionEP.LOCAL_INSPECTION.getName().equals(epName) || InspectionEP.GLOBAL_INSPECTION.getName().equals(epName)) {
          if (isInternal(element)) {
            return;
          }
          GenericAttributeValue implementationClass = getAttribute(element, "implementationClass");
          if (implementationClass == null || implementationClass.getStringValue() == null) {
            return;
          }
          GenericAttributeValue displayNameAttr = getAttribute(element, "displayName");
          if (displayNameAttr != null && displayNameAttr.getStringValue() != null) {
            holder.createProblem(element, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, getText(), null, new InspectionI18NQuickFix());
          }
        }
      }
    }
  }

  private static void highlightAction(@NotNull DomElementAnnotationHolder holder, @NotNull ActionOrGroup action) {
    String id = action.getId().getStringValue();
    if (id == null) return;

    String text = action.getText().getStringValue();
    String desc = action.getDescription().getStringValue();
    if (text == null && desc == null) return;

    if (isInternal(action)) return;

    holder.createProblem(action, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                         getText(),
                         null, createAnalyzeEPFix(action, id, text, desc));
  }

  private static boolean isInternal(@NotNull DomElement action) {
    final GenericAttributeValue internal = getAttribute(action, "internal");
    if (internal != null && "true".equals(internal.getStringValue())) return true;
    return false;
  }

  @NotNull
  private static String getText() {
    return DevKitBundle.message("inspections.plugin.xml.i18n.name");
  }

  private static LocalQuickFix createAnalyzeEPFix(ActionOrGroup ag, String id, String text, String desc) {
    return new IntentionAndQuickFixAction() {
      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getName() {
        return PluginXmlI18nInspection.getText();
      }

      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getFamilyName() {
        return PluginXmlI18nInspection.getText();
      }

      @Override
      public void applyFix(@NotNull Project project, PsiFile xmlFile, @Nullable Editor editor) {
        XmlElement xml = ag.getXmlElement();
        if (xml == null) return;
        @NonNls String prefix = ag instanceof Action ? "action" : "group";

        if (text != null) ag.getText().setStringValue(null);
        if (desc != null) ag.getDescription().setStringValue(null);

        PropertiesFileImpl propertiesFile = findBundlePropertiesFile(ag);

        PsiFile fileToWrite = propertiesFile != null ? propertiesFile : xmlFile;
        if (text != null) append(project, fileToWrite, prefix + "." + id + ".text=" + text);
        if (desc != null) append(project, fileToWrite, prefix + "." + id + ".description=" + desc);

        removeEmptyLines(xml);
      }

      private void removeEmptyLines(XmlElement xml) {
        xml.processElements(element -> {
          if (element instanceof PsiWhiteSpace && element.textContains('\n')) {
            PsiElement next = element.getNextSibling();
            if (next instanceof LeafPsiElement) {
              IElementType type = ((LeafPsiElement)next).getElementType();
              if (type == XmlTokenType.XML_TAG_END || type == XmlTokenType.XML_EMPTY_ELEMENT_END) {
                element.delete();
                return false;
              }
            }
          }
          return true;
        }, xml.getFirstChild());
      }

      private void append(@NotNull Project project, @NotNull PsiFile fileToWrite, @NonNls String text) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(fileToWrite);
        if (document == null) return;
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
        int length = document.getTextLength();
        document.insertString(length, "\n" + text);
        PsiDocumentManager.getInstance(project).commitDocument(document);
      }
    };
  }

  private static class InspectionI18NQuickFix implements LocalQuickFix, BatchQuickFix<CommonProblemDescriptor> {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return getText();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      XmlTag xml = (XmlTag)descriptor.getPsiElement();
      if (xml == null) return;
      choosePropertiesFileAndExtract(project, Collections.singletonList(xml));
    }

    @Override
    public void applyFix(@NotNull Project project,
                         CommonProblemDescriptor @NotNull [] descriptors,
                         @NotNull List<PsiElement> psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
      List<XmlTag> tags = new ArrayList<>();
      for (CommonProblemDescriptor d : descriptors) {
        if (d instanceof ProblemDescriptor) {
          PsiElement e = ((ProblemDescriptor)d).getPsiElement();
          if (e instanceof XmlTag) {
            tags.add((XmlTag)e);
          }
        }
      }
      choosePropertiesFileAndExtract(project, tags);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    private static void registerPropertyKey(@NotNull Project project, XmlTag xml, PropertiesFile propertiesFile) {
      String displayName = xml.getAttributeValue("displayName");
      if (displayName == null) return;
      xml.setAttribute("displayName", null);
      String shortName = xml.getAttributeValue( "shortName");
      if (shortName == null) {
        String implementationClass = xml.getAttributeValue("implementationClass");
        if (implementationClass == null) {
          return;
        }
        shortName = InspectionProfileEntry.getShortName(implementationClass);
      }
      String key = "inspection." + StringUtil.join(NameUtilCore.splitNameIntoWords(shortName), s -> StringUtil.decapitalize(s), ".") + ".display.name";
      xml.setAttribute("key", key);
      xml.setAttribute("bundle", getBundleQName(project, propertiesFile));

      JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                    Collections.singletonList(propertiesFile),
                                                                    key,
                                                                    StringUtil.unescapeXmlEntities(displayName),
                                                                    PsiExpression.EMPTY_ARRAY);
    }

    @NotNull
    private static String getBundleQName(@NotNull Project project,
                                         PropertiesFile propertiesFile) {
      String baseName = propertiesFile.getResourceBundle().getBaseName();
      VirtualFile virtualFile = propertiesFile.getVirtualFile();
      VirtualFile sourceRootForFile = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(virtualFile);
      if (sourceRootForFile != null) {
        String relativePath = VfsUtilCore.getRelativePath(virtualFile, sourceRootForFile, '.');
        if (relativePath != null) {
          return FileUtil.getNameWithoutExtension(relativePath);
        }
      }
      return baseName;
    }

    private static void choosePropertiesFileAndExtract(Project project, List<XmlTag> tags) {
      ResourceBundleManager resourceBundleManager;
      try {
        resourceBundleManager = ResourceBundleManager.getManager(ContainerUtil.map(tags, x -> x.getContainingFile()), project);
      }
      catch (ResourceBundleManager.ResourceBundleNotFoundException e) {
        resourceBundleManager = null;
      }
      @NotNull Set<Module> contextModules = ContainerUtil.map2Set(tags, x -> ModuleUtilCore.findModuleForPsiElement(x));
      List<String> files = resourceBundleManager != null ? resourceBundleManager.suggestPropertiesFiles(contextModules)
                                                         : I18nUtil.defaultSuggestPropertiesFiles(project, contextModules);
      if (files.isEmpty()) return;

      if (files.size() == 1) {
        doFix(project, files.get(0), tags);
        return;
      }

      final IPopupChooserBuilder<String> builder = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(files)
        .setNamerForFiltering(x -> x)
        .setTitle(DevKitBundle.message("inspections.plugin.xml.i18n.choose.bundle.4inspections.title")).
          setItemChosenCallback(selected -> {
            doFix(project, selected, tags);
          });
      builder.createPopup().showCenteredInCurrentWindow(project);
    }

    private static void doFix(Project project, String propertiesFilePath, List<XmlTag> tagsToUpdate) {
      VirtualFile propertiesVFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(propertiesFilePath));
      if (propertiesVFile != null){
        PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(PsiManager.getInstance(project).findFile(propertiesVFile));
        if (propertiesFile != null) {
          List<PsiFile> psiFiles = new ArrayList<>();
          psiFiles.add(propertiesFile.getContainingFile());
          for (XmlTag tag : tagsToUpdate) {
            psiFiles.add(tag.getContainingFile());
          }
          WriteCommandAction.runWriteCommandAction(project, getText(), null, () -> {
            for (XmlTag tag : tagsToUpdate) {
              registerPropertyKey(project, tag, propertiesFile);
            }
          }, psiFiles.toArray(PsiFile.EMPTY_ARRAY));
        }
      }
    }
  }
}
