// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n;

import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.ResourceBundleManager;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.notification.impl.NotificationGroupEP;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.SchemeConvertorEPBase;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.NameUtilCore;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase;
import org.jetbrains.idea.devkit.util.DescriptorI18nUtil;
import org.jetbrains.idea.devkit.util.PluginPlatformInfo;
import org.jetbrains.uast.UExpression;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PluginXmlI18nInspection extends DevKitPluginXmlInspectionBase {
  private static final Logger LOG = Logger.getInstance(PluginXmlI18nInspection.class);

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (element instanceof ActionOrGroup) {
      highlightActionOrGroup(holder, (ActionOrGroup)element);
    }
    else if (element instanceof Separator) {
      highlightSeparator(holder, (Separator)element);
    }
    else if (element instanceof OverrideText) {
      highlightOverrideText(holder, (OverrideText)element);
    }
    else if (element instanceof Extension) {
      highlightExtension(holder, (Extension)element);
    }
  }

  private static void highlightExtension(DomElementAnnotationHolder holder, Extension extension) {
    ExtensionPoint extensionPoint = extension.getExtensionPoint();
    if (extensionPoint == null) return;
    final PsiClass beanClass = extensionPoint.getBeanClass().getValue();
    if (beanClass == null) return;

    String epName = extensionPoint.getEffectiveQualifiedName();
    if (LocalInspectionEP.LOCAL_INSPECTION.getName().equals(epName) ||
        InspectionEP.GLOBAL_INSPECTION.getName().equals(epName)) {
      if (isInternal(extension, "isInternal")) {
        return;
      }
      GenericAttributeValue<?> implementationClass = getAttribute(extension, "implementationClass");
      if (implementationClass == null || implementationClass.getStringValue() == null) {
        return;
      }
      checkNonLocalizableAttribute(holder, extension, "displayName", new InspectionI18NQuickFix());
      checkNonLocalizableAttribute(holder, extension, "groupName", null);
      //checkNonLocalizableAttribute(holder, element, "groupPath", null);
    }
    else if (IntentionManagerImpl.EP_INTENTION_ACTIONS.getName().equals(epName)) {
      checkNonLocalizableTag(holder, extension, "category", null);
    }
    else if (InheritanceUtil.isInheritor(beanClass, ConfigurableEP.class.getName())) {
      checkNonLocalizableAttribute(holder, extension, "displayName", null);

      // ConfigurableEP#children
      for (DomElement nestedConfigurable : DomUtil.getDefinedChildren(extension, true, false)) {
        checkNonLocalizableAttribute(holder, nestedConfigurable, "displayName", null);
      }
    }
    else if (InheritanceUtil.isInheritor(beanClass, SchemeConvertorEPBase.class.getName())) {
      checkNonLocalizableAttribute(holder, extension, "name", null);
    } else if (NotificationGroupEP.class.getName().equals(beanClass.getQualifiedName())){
      if (hasMissingAttribute(extension, "key")) {
        GenericAttributeValue<?> value = getAttribute(extension, "hideFromSettings");
        if (value == null || !Boolean.parseBoolean(value.getStringValue())) {
          holder.createProblem(extension, DevKitI18nBundle.message("inspections.plugin.xml.i18n.name"),
                               new NotificationGroupI18NQuickFix());
        }
      }
    }
  }

  private static void checkNonLocalizableAttribute(DomElementAnnotationHolder holder,
                                                   DomElement element,
                                                   @NonNls String attributeName,
                                                   @Nullable LocalQuickFix fix) {
    highlightNonLocalizableElement(holder, getAttribute(element, attributeName), attributeName, fix);
  }

  private static void checkNonLocalizableTag(DomElementAnnotationHolder holder,
                                             DomElement element,
                                             String tagName,
                                             @Nullable LocalQuickFix quickFix) {
    highlightNonLocalizableElement(holder, getTag(element, tagName), tagName, quickFix);
  }

  private static void highlightNonLocalizableElement(DomElementAnnotationHolder holder,
                                                     GenericDomValue<?> valueElement,
                                                     @NonNls String valueElementName,
                                                     @Nullable LocalQuickFix fix) {
    if (valueElement != null && valueElement.getStringValue() != null) {
      holder.createProblem(valueElement,
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                           DevKitI18nBundle.message("inspections.plugin.xml.i18n.inspection.tag.family.name", valueElementName), null, fix);
    }
  }

  private static void highlightSeparator(DomElementAnnotationHolder holder, Separator separator) {
    if (!DomUtil.hasXml(separator.getText())) return;

    final BuildNumber buildNumber = PluginPlatformInfo.forDomElement(separator).getSinceBuildNumber();
    if (buildNumber != null && buildNumber.getBaselineVersion() >= 202) {
      holder.createProblem(separator, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                           DevKitI18nBundle.message("inspections.plugin.xml.i18n.key"),
                           null, new SeparatorKeyI18nQuickFix());
    }
  }

  private static void highlightOverrideText(DomElementAnnotationHolder holder, OverrideText overrideText) {
    if (!DomUtil.hasXml(overrideText.getText())) return;

    DomElement parent = overrideText.getParent();
    PropertiesFile propertiesFile = DescriptorI18nUtil.findBundlePropertiesFile(parent);

    holder.createProblem(overrideText, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                         DevKitI18nBundle.message("inspections.plugin.xml.i18n.name"),
                         null, new ActionOrGroupQuickFixAction(propertiesFile != null ? propertiesFile.getVirtualFile() : null,
                                                               parent instanceof Action));
  }

  private static void highlightActionOrGroup(@NotNull DomElementAnnotationHolder holder, @NotNull ActionOrGroup actionOrGroup) {
    String id = actionOrGroup.getId().getStringValue();
    if (id == null) return;

    String text = actionOrGroup.getText().getStringValue();
    String desc = actionOrGroup.getDescription().getStringValue();
    if (text == null && desc == null) return;

    if (isInternal(actionOrGroup, "internal")) return;

    PropertiesFile propertiesFile = DescriptorI18nUtil.findBundlePropertiesFile(actionOrGroup);

    holder.createProblem(actionOrGroup, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                         DevKitI18nBundle.message("inspections.plugin.xml.i18n.name"),
                         null, new ActionOrGroupQuickFixAction(propertiesFile != null ? propertiesFile.getVirtualFile() : null,
                                                               actionOrGroup instanceof Action));
  }

  private static boolean isInternal(@NotNull DomElement action, String internalAttributeName) {
    final GenericAttributeValue<?> internalAttribute = getAttribute(action, internalAttributeName);
    if (internalAttribute != null && "true".equals(internalAttribute.getStringValue())) return true;
    return false;
  }

  private static void choosePropertiesFileAndExtract(Project project, List<XmlTag> tags, Consumer<String> doFixConsumer) {
    ResourceBundleManager resourceBundleManager;
    try {
      resourceBundleManager = ResourceBundleManager.getManager(ContainerUtil.map(tags, x -> x.getContainingFile()), project);
    }
    catch (ResourceBundleManager.ResourceBundleNotFoundException e) {
      resourceBundleManager = null;
    }
    @NotNull Set<Module> contextModules = ContainerUtil.map2Set(tags, x -> ModuleUtilCore.findModuleForPsiElement(x));
    ResourceBundleManager finalResourceBundleManager = resourceBundleManager;
    List<String> files = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.compute(() -> finalResourceBundleManager != null ? finalResourceBundleManager.suggestPropertiesFiles(contextModules) 
                                                                        : I18nUtil.defaultSuggestPropertiesFiles(project, contextModules)),
      DevKitBundle.message("progress.title.calculate.target.properties.file"), true, project);

    if (files == null || files.isEmpty()) return;

    if (files.size() == 1) {
      doFixConsumer.accept(files.get(0));
      return;
    }

    final IPopupChooserBuilder<String> builder = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(files)
      .setNamerForFiltering(x -> x)
      .setTitle(DevKitI18nBundle.message("inspections.plugin.xml.i18n.choose.bundle.4inspections.title")).
        setItemChosenCallback(selected -> {
          doFixConsumer.accept(selected);
        });
    builder.createPopup().showCenteredInCurrentWindow(project);
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


  private static List<XmlTag> getTags(List<CommonProblemDescriptor> descriptors) {
    List<XmlTag> tags = new ArrayList<>();
    for (CommonProblemDescriptor d : descriptors) {
      if (d instanceof ProblemDescriptor) {
        PsiElement e = ((ProblemDescriptor)d).getPsiElement();
        if (e instanceof XmlTag) {
          tags.add((XmlTag)e);
        }
        else if (e instanceof XmlAttributeValue) {
          PsiElement parent = e.getParent();
          if (parent instanceof XmlAttribute) {
            ContainerUtil.addIfNotNull(tags, ((XmlAttribute)parent).getParent());
          }
        }
      }
    }
    return tags;
  }

  private static @Nullable PropertiesFile findPropertiesFile(Project project, String propertiesFilePath) {
    VirtualFile propertiesVFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(propertiesFilePath));
    if (propertiesVFile != null) {
      return PropertiesImplUtil.getPropertiesFile(PsiManager.getInstance(project).findFile(propertiesVFile));
    }
    return null;
  }

  private static class NotificationGroupI18NQuickFix implements LocalQuickFix {

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return IntentionPreviewInfo.EMPTY;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return DevKitI18nBundle.message("inspections.plugin.xml.i18n.inspection.tag.family.name", "key");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement psiElement = descriptor.getPsiElement();
      if (!(psiElement instanceof XmlTag)) return;
      XmlTag xmlTag = (XmlTag)psiElement;


      choosePropertiesFileAndExtract(project, Collections.singletonList(xmlTag), selection -> {
        PropertiesFile propertiesFile = findPropertiesFile(project, selection);
        if (propertiesFile != null) {
          WriteCommandAction.runWriteCommandAction(
            project, DevKitI18nBundle.message("inspections.plugin.xml.i18n.key.command.name"), null, () -> {
              XmlAttribute bundleAttribute = xmlTag.getAttribute("bundle");
              String bundleQName = getBundleQName(project, propertiesFile);
              if (bundleAttribute == null || !bundleQName.equals(bundleAttribute.getValue())) {
                xmlTag.setAttribute("bundle", bundleQName);
              }

              String escapedId = StringUtil.defaultIfEmpty(StringUtil.toLowerCase(xmlTag.getAttributeValue("id")), "notification.id").replace(' ', '.');
              String messageKey = "notification.group." + escapedId;
              xmlTag.setAttribute("key", messageKey);

              JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                            Collections.singletonList(propertiesFile),
                                                                            messageKey,
                                                                            "",
                                                                            new UExpression[0]);

              IProperty createdProperty = propertiesFile.findPropertyByKey(messageKey);
              if (createdProperty != null) {
                createdProperty.navigate(true);
              }
            }, psiElement.getContainingFile(), propertiesFile.getContainingFile());
        }
      });
    }
  }

  private static class InspectionI18NQuickFix implements LocalQuickFix, BatchQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return DevKitI18nBundle.message("inspections.plugin.xml.i18n.inspection.tag.family.name", "displayName");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      XmlTag xml = null;
      if (element instanceof XmlTag) {
        xml = (XmlTag)element;
      }
      else if (element instanceof XmlAttributeValue) {
        PsiElement parent = element.getParent();
        xml = parent instanceof XmlAttribute ? ((XmlAttribute)parent).getParent() : null;
      }
      if (xml == null) return;
      doFix(project, Collections.singletonList(xml));
    }

    @Override
    public void applyFix(@NotNull Project project,
                         CommonProblemDescriptor @NotNull [] descriptors,
                         @NotNull List<PsiElement> psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
      doFix(project, getTags(Arrays.asList(descriptors)));
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    private static void doFix(@NotNull Project project, List<XmlTag> tags) {
      choosePropertiesFileAndExtract(project, tags, selection -> {
        PropertiesFile propertiesFile = findPropertiesFile(project, selection);
        if (propertiesFile != null) {
          List<PsiFile> psiFiles = new ArrayList<>();
          psiFiles.add(propertiesFile.getContainingFile());
          for (XmlTag tag : tags) {
            psiFiles.add(tag.getContainingFile());
          }
          WriteCommandAction.runWriteCommandAction(
            project, DevKitI18nBundle.message("inspections.plugin.xml.i18n.inspection.tag.command.name", "displayName"), null, () -> {
              for (XmlTag tag : tags) {
                registerPropertyKey(project, tag, propertiesFile);
              }
            }, psiFiles.toArray(PsiFile.EMPTY_ARRAY));
        }
      });
    }

    private static void registerPropertyKey(@NotNull Project project, XmlTag xml, PropertiesFile propertiesFile) {
      String displayName = xml.getAttributeValue("displayName");
      if (displayName == null) return;
      xml.setAttribute("displayName", null);
      String shortName = xml.getAttributeValue("shortName");
      if (shortName == null) {
        String implementationClass = xml.getAttributeValue("implementationClass");
        if (implementationClass == null) {
          return;
        }
        shortName = InspectionProfileEntry.getShortName(StringUtil.getShortName(implementationClass));
      }
      @NonNls String key =
        "inspection." + StringUtil.join(NameUtilCore.splitNameIntoWords(shortName), s -> StringUtil.decapitalize(s), ".") +
        ".display.name";
      xml.setAttribute("key", key);

      XmlTag rootTag = ((XmlFile)xml.getContainingFile()).getRootTag();
      XmlTag resourceBundle = rootTag != null ? rootTag.findFirstSubTag("resource-bundle") : null;
      String bundleQName = getBundleQName(project, propertiesFile);
      if (resourceBundle == null || !bundleQName.equals(resourceBundle.getValue().getTrimmedText())) {
        xml.setAttribute("bundle", bundleQName);
      }

      JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                    Collections.singletonList(propertiesFile),
                                                                    key,
                                                                    StringUtil.unescapeXmlEntities(displayName),
                                                                    new UExpression[0]);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return IntentionPreviewInfo.EMPTY;
    }
  }

  private static final class ActionOrGroupQuickFixAction implements LocalQuickFix, BatchQuickFix {
    private final VirtualFile myPropertiesFile;
    private final boolean myIsAction;

    private ActionOrGroupQuickFixAction(VirtualFile file, boolean isAction) {
      myPropertiesFile = file;
      myIsAction = isAction;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return DevKitI18nBundle.message("inspections.plugin.xml.i18n.name");
    }

    @Override
    public void applyFix(@NotNull Project project,
                         CommonProblemDescriptor @NotNull [] descriptors,
                         @NotNull List<PsiElement> psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
      VirtualFile nullValue = new LightVirtualFile();
      Map<VirtualFile, List<CommonProblemDescriptor>> byPropertyFiles = Arrays.stream(descriptors)
        .filter(d -> d instanceof ProblemDescriptor)
        .collect(Collectors.groupingBy(cd -> ObjectUtils.notNull(((ActionOrGroupQuickFixAction)cd.getFixes()[0]).myPropertiesFile, nullValue)));

      //in case of multiple files in selection with different bundles, multiple clear read-only status dialog is possible
      for (Map.Entry<VirtualFile, List<CommonProblemDescriptor>> entry : byPropertyFiles.entrySet()) {
        VirtualFile propertyFile = entry.getKey().equals(nullValue) ? null : entry.getKey();
        doFix(project, propertyFile, getTags(entry.getValue()));
      }
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof XmlTag)) return;
      XmlTag tag = (XmlTag)element;

      doFix(project, myPropertiesFile, Collections.singletonList(tag));
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    private void doFix(@NotNull Project project, VirtualFile file, List<XmlTag> tags) {
      if (file == null) {
        choosePropertiesFileAndExtract(project, tags, selection -> {
          PropertiesFile propertiesFile = findPropertiesFile(project, selection);
          doFix(project, tags, propertiesFile, true);
        });
      }
      else {
        PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(PsiManager.getInstance(project).findFile(file));
        doFix(project, tags, propertiesFile, false);
      }
    }

    private void doFix(@NotNull Project project,
                       List<XmlTag> tags,
                       PropertiesFile propertiesFile,
                       boolean attachResourceBundle) {
      if (propertiesFile != null) {
        List<PsiFile> psiFiles = new ArrayList<>();
        psiFiles.add(propertiesFile.getContainingFile());
        for (XmlTag t : tags) {
          psiFiles.add(t.getContainingFile());
        }
        WriteCommandAction
          .runWriteCommandAction(project, DevKitI18nBundle.message("inspections.plugin.xml.i18n.command.name"), null, () -> {
                                   if (attachResourceBundle) {
                                     createResourceBundleTag(project, tags, propertiesFile);
                                   }
                                   extractTextAndDescription(project, tags, propertiesFile, myIsAction);
                                 },
                                 psiFiles.toArray(PsiFile.EMPTY_ARRAY));
      }
    }

    private static void createResourceBundleTag(@NotNull Project project, List<XmlTag> tags, PropertiesFile propertiesFile) {
      for (XmlTag tag : tags) {
        XmlTag rootTag = ((XmlFile)tag.getContainingFile()).getRootTag();
        LOG.assertTrue(rootTag != null);
        if (rootTag.findFirstSubTag("resource-bundle") == null) {
          XmlElementFactory elementFactory = XmlElementFactory.getInstance(project);
          XmlTag rbTag = elementFactory.createTagFromText("<resource-bundle>" +
                                                          getBundleQName(project, propertiesFile) +
                                                          "</resource-bundle>");

          rootTag.addSubTag(rbTag, false);
        }
      }
    }

    private static void extractTextAndDescription(@NotNull Project project,
                                                  Collection<XmlTag> tags,
                                                  PropertiesFile propertiesFile,
                                                  boolean isAction) {
      for (XmlTag tag : tags) {
        String text = tag.getAttributeValue("text");
        tag.setAttribute("text", null);
        String description = tag.getAttributeValue("description");
        tag.setAttribute("description", null);

        String id;
        if (tag.getName().equals("override-text")) {
          id = Objects.requireNonNull(tag.getParentTag()).getAttributeValue("id") + "." + tag.getAttributeValue("place");
        }
        else {
          id = tag.getAttributeValue("id");
        }

        List<PropertiesFile> propertiesFiles = Collections.singletonList(propertiesFile);
        @NonNls String actionOrGroupPrefix = isAction ? "action." : "group.";
        if (text != null) {
          JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                        propertiesFiles,
                                                                        actionOrGroupPrefix + id + ".text",
                                                                        text,
                                                                        new UExpression[0]);
        }
        if (description != null) {
          JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                        propertiesFiles,
                                                                        actionOrGroupPrefix + id + ".description",
                                                                        description,
                                                                        new UExpression[0]);
        }

        tag.processElements(element -> {
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
        }, tag.getFirstChild());
      }
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return IntentionPreviewInfo.EMPTY;
    }
  }


  private static class SeparatorKeyI18nQuickFix implements LocalQuickFix, BatchQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return DevKitI18nBundle.message("inspections.plugin.xml.i18n.key");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      XmlTag xml = (XmlTag)descriptor.getPsiElement();
      if (xml == null) return;
      doFix(project, Collections.singletonList(xml));
    }

    @Override
    public void applyFix(@NotNull Project project,
                         CommonProblemDescriptor @NotNull [] descriptors,
                         @NotNull List<PsiElement> psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
      doFix(project, getTags(Arrays.asList(descriptors)));
    }


    private static void doFix(@NotNull Project project, List<XmlTag> tags) {
      choosePropertiesFileAndExtract(project, tags, selection -> {
        PropertiesFile propertiesFile = findPropertiesFile(project, selection);
        if (propertiesFile != null) {
          List<PsiFile> psiFiles = new ArrayList<>();
          psiFiles.add(propertiesFile.getContainingFile());
          for (XmlTag tag : tags) {
            psiFiles.add(tag.getContainingFile());
          }
          WriteCommandAction.runWriteCommandAction(
            project, DevKitI18nBundle.message("inspections.plugin.xml.i18n.key.command.name"), null, () -> {
              for (XmlTag tag : tags) {
                registerPropertyKey(project, tag, propertiesFile);
              }
            }, psiFiles.toArray(PsiFile.EMPTY_ARRAY));
        }
      });
    }

    private static void registerPropertyKey(@NotNull Project project, XmlTag xml, PropertiesFile propertiesFile) {
      final DomElement domElement = DomUtil.getDomElement(xml);
      assert domElement instanceof Separator;

      Separator separator = (Separator)domElement;

      String text = StringUtil.defaultIfEmpty(separator.getText().getStringValue(), "noText");
      @NonNls String key = "separator." + StringUtil.join(NameUtilCore.splitNameIntoWords(text),
                                                          s -> StringUtil.trim(StringUtil.decapitalize(s)), ".");

      JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                    Collections.singletonList(propertiesFile),
                                                                    key,
                                                                    StringUtil.unescapeXmlEntities(text),
                                                                    new UExpression[0]);
      separator.getText().undefine();
      separator.getKey().setValue(key);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return IntentionPreviewInfo.EMPTY;
    }
  }
}
