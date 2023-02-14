// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.ide.TypeNameEP;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.ResourceBundleReference;
import com.intellij.notification.impl.NotificationGroupEP;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SchemeConvertorEPBase;
import com.intellij.openapi.options.advanced.AdvancedSettingBean;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Iconable;
import com.intellij.patterns.DomPatterns;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.patterns.XmlTagPattern;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.IconDescriptionBundleEP;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.references.MessageBundleReferenceContributor;
import org.jetbrains.idea.devkit.util.PsiUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.patterns.XmlPatterns.*;

public class I18nReferenceContributor extends PsiReferenceContributor {

  @NonNls private static final String INTENTION_ACTION_TAG = "intentionAction";
  @NonNls private static final String INTENTION_ACTION_BUNDLE_TAG = "bundleName";

  @NonNls private static final String SEPARATOR_TAG = "separator";
  @NonNls private static final String SYNONYM_TAG = "synonym";

  @NonNls
  private static class Holder {
    private static final String GROUP_CONFIGURABLE_EP = "com.intellij.openapi.options.ex.ConfigurableGroupEP";
    private static final String CONFIGURABLE_EP = ConfigurableEP.class.getName();
    private static final String INSPECTION_EP = InspectionEP.class.getName();

    private static final String NOTIFICATION_GROUP_EP = NotificationGroupEP.class.getName();
    private static final String SCHEME_CONVERTER_EP = SchemeConvertorEPBase.class.getName();

    private static final String ICON_DESCRIPTION_BUNDLE_EP = IconDescriptionBundleEP.class.getName();
    private static final String TYPE_NAME_EP = TypeNameEP.class.getName();
    private static final String ADVANCED_SETTINGS_EP = AdvancedSettingBean.class.getName();

    private static final String SPRING_TOOL_WINDOW_CONTENT = "com.intellij.spring.toolWindow.SpringToolWindowContent";
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registerKeyProviders(registrar);

    registerBundleNameProviders(registrar);

    registerLiveTemplateSetXml(registrar);
  }

  private static void registerKeyProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"key", "groupKey"},
                                                                  Holder.CONFIGURABLE_EP, Holder.INSPECTION_EP,
                                                                  Holder.NOTIFICATION_GROUP_EP),
                                        new PropertyKeyReferenceProvider(false, "groupKey", "groupBundle"));
    registrar.registerReferenceProvider(nestedExtensionAttributePattern(new String[]{"key", "groupKey"},
                                                                        Holder.CONFIGURABLE_EP),
                                        new PropertyKeyReferenceProvider(false, "groupKey", "groupBundle"));

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"groupPathKey"}, Holder.INSPECTION_EP),
                                        new PropertyKeyReferenceProvider(false, "groupPathKey", "groupBundle"));

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"nameKey"}, Holder.SCHEME_CONVERTER_EP),
                                        new PropertyKeyReferenceProvider(false, "nameKey", "nameBundle"));

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"displayNameKey"},
                                                                  Holder.GROUP_CONFIGURABLE_EP),
                                        new PropertyKeyReferenceProvider(false, "displayNameKey", null) {
                                          @Override
                                          protected String getFallbackBundleName() {
                                            return OptionsBundle.BUNDLE;
                                          }
                                        });
    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"descriptionKey"},
                                                                  Holder.GROUP_CONFIGURABLE_EP),
                                        new PropertyKeyReferenceProvider(false, "descriptionKey", null) {
                                          @Override
                                          protected String getFallbackBundleName() {
                                            return OptionsBundle.BUNDLE;
                                          }
                                        });

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"resourceKey"},
                                                                  Holder.TYPE_NAME_EP),
                                        new PropertyKeyReferenceProvider(false, "resourceKey", "resourceBundle"));

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"displayName"},
                                                                  Holder.SPRING_TOOL_WINDOW_CONTENT),
                                        new PropertyKeyReferenceProvider(false, "displayName", "bundle"));


    XmlAttributeValuePattern idAttributeWithoutTitleKeyPattern =
      extensionAttributePattern(new String[]{"id"}, Holder.ADVANCED_SETTINGS_EP)
        .andNot(xmlAttributeValue().withSuperParent(2, xmlTag().withChild(xmlAttribute("titleKey"))));
    registrar.registerReferenceProvider(idAttributeWithoutTitleKeyPattern,
                                        new PropertyKeyReferenceProvider(false, "id", null) {
                                          @Override
                                          protected @NotNull String getFinalKeyValue(String keyValue) {
                                            return MessageBundleReferenceContributor.ADVANCED_SETTING + keyValue;
                                          }

                                          @Override
                                          protected String getFallbackBundleName() {
                                            return ApplicationBundle.BUNDLE;
                                          }
                                        });
    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"titleKey"}, Holder.ADVANCED_SETTINGS_EP),
                                        new PropertyKeyReferenceProvider(false, "titleKey", null) {
                                          @Override
                                          protected String getFallbackBundleName() {
                                            return ApplicationBundle.BUNDLE;
                                          }
                                        });
    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"groupKey"}, Holder.ADVANCED_SETTINGS_EP),
                                        new PropertyKeyReferenceProvider(false, "groupKey", null) {
                                          @Override
                                          protected String getFallbackBundleName() {
                                            return ApplicationBundle.BUNDLE;
                                          }
                                        });
    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"descriptionKey"}, Holder.ADVANCED_SETTINGS_EP),
                                        new PropertyKeyReferenceProvider(false, "descriptionKey", null) {
                                          @Override
                                          protected String getFallbackBundleName() {
                                            return ApplicationBundle.BUNDLE;
                                          }
                                        });
    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"trailingLabelKey"}, Holder.ADVANCED_SETTINGS_EP),
                                        new PropertyKeyReferenceProvider(false, "trailingLabelKey", null) {
                                          @Override
                                          protected String getFallbackBundleName() {
                                            return ApplicationBundle.BUNDLE;
                                          }
                                        });

    final XmlAttributeValuePattern separatorSynonymPattern =
      xmlAttributeValue("key")
        .withSuperParent(2,
                         or(DomPatterns.tagWithDom(SEPARATOR_TAG, Separator.class),
                            DomPatterns.tagWithDom(SYNONYM_TAG, Synonym.class)));
    registrar.registerReferenceProvider(separatorSynonymPattern,
                                        new PropertyKeyReferenceProvider(tag -> {
                                          final DomElement domElement = DomUtil.getDomElement(tag);
                                          if (domElement == null) return null;

                                          final Actions actions = DomUtil.getParentOfType(domElement, Actions.class, true);
                                          return actions != null ? actions.getResourceBundle().getStringValue() : null;
                                        }));

    final XmlTagPattern.Capture intentionActionKeyTagPattern =
      xmlTag().withLocalName("categoryKey").
        withParent(DomPatterns.tagWithDom(INTENTION_ACTION_TAG, Extension.class));
    registrar.registerReferenceProvider(intentionActionKeyTagPattern,
                                        new PropertyKeyReferenceProvider(true, null, INTENTION_ACTION_BUNDLE_TAG));
  }

  private static void registerBundleNameProviders(PsiReferenceRegistrar registrar) {
    final PsiReferenceProvider bundleReferenceProvider = createBundleReferenceProvider();

    final XmlTagPattern.Capture resourceBundleTagPattern =
      xmlTag().withLocalName("resource-bundle").
        withParent(DomPatterns.tagWithDom(IdeaPlugin.TAG_NAME, IdeaPlugin.class));
    registrar.registerReferenceProvider(resourceBundleTagPattern, bundleReferenceProvider);

    final XmlAttributeValuePattern actionsResourceBundlePattern =
      xmlAttributeValue("resource-bundle")
        .withSuperParent(2, DomPatterns.tagWithDom("actions", Actions.class));
    registrar.registerReferenceProvider(actionsResourceBundlePattern, bundleReferenceProvider);

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"bundle", "groupBundle"},
                                                                  Holder.CONFIGURABLE_EP, Holder.INSPECTION_EP,
                                                                  Holder.GROUP_CONFIGURABLE_EP,
                                                                  Holder.NOTIFICATION_GROUP_EP,
                                                                  Holder.SPRING_TOOL_WINDOW_CONTENT,
                                                                  Holder.ADVANCED_SETTINGS_EP),
                                        bundleReferenceProvider);
    registrar.registerReferenceProvider(nestedExtensionAttributePattern(new String[]{"bundle", "groupBundle"},
                                                                        Holder.CONFIGURABLE_EP),
                                        bundleReferenceProvider);

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"nameBundle"},
                                                                  Holder.SCHEME_CONVERTER_EP),
                                        bundleReferenceProvider);

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"resourceBundle"},
                                                                  Holder.TYPE_NAME_EP, Holder.ICON_DESCRIPTION_BUNDLE_EP),
                                        bundleReferenceProvider);

    final XmlTagPattern.Capture intentionActionBundleTagPattern =
      xmlTag().withLocalName(INTENTION_ACTION_BUNDLE_TAG).
        withParent(DomPatterns.tagWithDom(INTENTION_ACTION_TAG, Extension.class));
    registrar.registerReferenceProvider(intentionActionBundleTagPattern, bundleReferenceProvider);
  }

  private static XmlAttributeValuePattern extensionAttributePattern(@NonNls String[] attributeNames,
                                                                    @NonNls String... extensionPointClassNames) {
    return xmlAttributeValue(attributeNames)
      .inFile(DomPatterns.inDomFile(IdeaPlugin.class))
      .withSuperParent(2, extensionPointCapture(extensionPointClassNames));
  }

  // special case for nested EPs, ConfigurableEP#children
  private static XmlAttributeValuePattern nestedExtensionAttributePattern(@NonNls String[] attributeNames,
                                                                          @NonNls String... extensionPointClassNames) {
    return xmlAttributeValue(attributeNames)
      .inFile(DomPatterns.inDomFile(IdeaPlugin.class))
      .withSuperParent(3, extensionPointCapture(extensionPointClassNames));
  }

  @NotNull
  private static XmlTagPattern.Capture extensionPointCapture(@NonNls String[] extensionPointClassNames) {
    return xmlTag()
      .and(DomPatterns.withDom(DomPatterns.domElement(Extension.class).with(new PatternCondition<>("relevantEP") {
        @Override
        public boolean accepts(@NotNull Extension extension,
                               ProcessingContext context) {
          final ExtensionPoint extensionPoint = extension.getExtensionPoint();
          if (extensionPoint == null) return false;

          final PsiClass beanClass = extensionPoint.getBeanClass().getValue();
          for (String name : extensionPointClassNames) {
            if (InheritanceUtil.isInheritor(beanClass, name)) return true;
          }
          return false;
        }
      })));
  }

  private static void registerLiveTemplateSetXml(PsiReferenceRegistrar registrar) {
    final XmlTagPattern.Capture templateTagCapture =
      xmlTag().withLocalName("template")
        .withParent(xmlTag().withLocalName("templateSet"))
        .with(new PatternCondition<>("pluginProject") {
          @Override
          public boolean accepts(@NotNull XmlTag tag, ProcessingContext context) {
            return PsiUtil.isPluginProject(tag.getProject());
          }
        });

    registrar.registerReferenceProvider(xmlAttributeValue("key").withSuperParent(2, templateTagCapture),
                                        new PropertyKeyReferenceProvider(tag -> tag.getAttributeValue("resource-bundle")));


    registrar.registerReferenceProvider(xmlAttributeValue("resource-bundle").withSuperParent(2, templateTagCapture),
                                        createBundleReferenceProvider());
  }

  @NotNull
  private static PsiReferenceProvider createBundleReferenceProvider() {
    return new PsiReferenceProvider() {

      @Override
      public boolean acceptsTarget(@NotNull PsiElement target) {
        return target instanceof PsiFile && PropertiesImplUtil.isPropertiesFile((PsiFile)target);
      }

      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                             @NotNull ProcessingContext context) {
        return new PsiReference[]{new MyResourceBundleReference(element)};
      }
    };
  }


  private static final class MyResourceBundleReference extends ResourceBundleReference implements EmptyResolveMessageProvider {

    private MyResourceBundleReference(PsiElement element) {
      super(element, false);
    }

    @Override
    public Object @NotNull [] getVariants() {
      final Project project = myElement.getProject();
      PropertiesReferenceManager referenceManager = PropertiesReferenceManager.getInstance(project);
      final List<LookupElement> variants = new ArrayList<>();
      referenceManager.processPropertiesFiles(GlobalSearchScopesCore.projectProductionScope(project), (baseName, propertiesFile) -> {
        final Icon icon = propertiesFile.getContainingFile().getIcon(Iconable.ICON_FLAG_READ_STATUS);
        final String relativePath = ProjectUtil.calcRelativeToProjectPath(propertiesFile.getVirtualFile(), project);
        variants.add(LookupElementBuilder.create(propertiesFile, baseName)
                       .withIcon(icon)
                       .withTailText(" (" + relativePath + ")", true));
        return true;
      }, this);
      return variants.toArray(LookupElement.EMPTY_ARRAY);
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return DevKitBundle.message("plugin.xml.convert.property.bundle.cannot.resolve");
    }
  }
}