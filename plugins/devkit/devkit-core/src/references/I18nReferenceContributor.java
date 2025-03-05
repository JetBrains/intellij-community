// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.hints.declarative.InlayHintsProviderExtensionBean;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.ide.TypeNameEP;
import com.intellij.notification.impl.NotificationGroupEP;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SchemeConvertorEPBase;
import com.intellij.openapi.options.advanced.AdvancedSettingBean;
import com.intellij.patterns.DomPatterns;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.patterns.XmlTagPattern;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.IconDescriptionBundleEP;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.util.PsiUtil;

import static com.intellij.patterns.XmlPatterns.*;

final class I18nReferenceContributor extends PsiReferenceContributor {

  private static final @NonNls String INTENTION_ACTION_TAG = "intentionAction";
  private static final @NonNls String INTENTION_ACTION_BUNDLE_TAG = "bundleName";

  private static final @NonNls String SEPARATOR_TAG = "separator";
  private static final @NonNls String SYNONYM_TAG = "synonym";

  private static @NonNls class Holder {
    private static final String GROUP_CONFIGURABLE_EP = "com.intellij.openapi.options.ex.ConfigurableGroupEP";
    private static final String CONFIGURABLE_EP = ConfigurableEP.class.getName();
    private static final String INSPECTION_EP = InspectionEP.class.getName();

    private static final String NOTIFICATION_GROUP_EP = NotificationGroupEP.class.getName();
    private static final String SCHEME_CONVERTER_EP = SchemeConvertorEPBase.class.getName();

    private static final String ICON_DESCRIPTION_BUNDLE_EP = IconDescriptionBundleEP.class.getName();
    private static final String TYPE_NAME_EP = TypeNameEP.class.getName();
    private static final String ADVANCED_SETTINGS_EP = AdvancedSettingBean.class.getName();

    private static final String SPRING_TOOL_WINDOW_CONTENT = "com.intellij.spring.toolWindow.SpringToolWindowContent";

    private static final String DECLARATIVE_INLAY_PROVIDER_EP = InlayHintsProviderExtensionBean.class.getName();

    private static final String WEB_SYMBOLS_INSPECTION_TOOL_MAPPING_EP =
      "com.intellij.webSymbols.inspections.impl.WebSymbolsInspectionToolMappingEP";
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

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"messageKey"},
                                                                  Holder.WEB_SYMBOLS_INSPECTION_TOOL_MAPPING_EP),
                                        new PropertyKeyReferenceProvider(false, "messageKey", "bundleName"));


    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"nameKey"},
                                                                  Holder.DECLARATIVE_INLAY_PROVIDER_EP),
                                        new PropertyKeyReferenceProvider(false, "nameKey", null));
    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"descriptionKey"},
                                                                  Holder.DECLARATIVE_INLAY_PROVIDER_EP),
                                        new PropertyKeyReferenceProvider(false, "descriptionKey", null));
    registrar.registerReferenceProvider(nestedTagExtensionAttributePattern("option", new String[]{"nameKey"},
                                                                           Holder.DECLARATIVE_INLAY_PROVIDER_EP),
                                        new PropertyKeyReferenceProvider(false, "nameKey", null));
    registrar.registerReferenceProvider(nestedTagExtensionAttributePattern("option", new String[]{"descriptionKey"},
                                                                           Holder.DECLARATIVE_INLAY_PROVIDER_EP),
                                        new PropertyKeyReferenceProvider(false, "descriptionKey", null));


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
    final PsiReferenceProvider bundleReferenceProvider = new ResourceBundlePsiReferenceProvider();

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
                                                                  Holder.ADVANCED_SETTINGS_EP,
                                                                  Holder.DECLARATIVE_INLAY_PROVIDER_EP),
                                        bundleReferenceProvider);
    registrar.registerReferenceProvider(nestedExtensionAttributePattern(new String[]{"bundle", "groupBundle"},
                                                                        Holder.CONFIGURABLE_EP),
                                        bundleReferenceProvider);
    registrar.registerReferenceProvider(nestedTagExtensionAttributePattern("option", new String[]{"bundle"},
                                                                           Holder.DECLARATIVE_INLAY_PROVIDER_EP),
                                        bundleReferenceProvider);

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"nameBundle"},
                                                                  Holder.SCHEME_CONVERTER_EP),
                                        bundleReferenceProvider);

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"resourceBundle"},
                                                                  Holder.TYPE_NAME_EP, Holder.ICON_DESCRIPTION_BUNDLE_EP),
                                        bundleReferenceProvider);

    registrar.registerReferenceProvider(extensionAttributePattern(new String[]{"bundleName"},
                                                                  Holder.WEB_SYMBOLS_INSPECTION_TOOL_MAPPING_EP),
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

  // special case for nested tag in EPs
  private static XmlAttributeValuePattern nestedTagExtensionAttributePattern(@NonNls String tagName,
                                                                             @NonNls String[] attributeNames,
                                                                             @NonNls String... extensionPointClassNames) {
    return xmlAttributeValue(attributeNames)
      .inFile(DomPatterns.inDomFile(IdeaPlugin.class))
      .withSuperParent(2, xmlTag().withLocalName(tagName))
      .withSuperParent(3, extensionPointCapture(extensionPointClassNames));
  }

  private static @NotNull XmlTagPattern.Capture extensionPointCapture(@NonNls String[] extensionPointClassNames) {
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
                                        new ResourceBundlePsiReferenceProvider());
  }
}