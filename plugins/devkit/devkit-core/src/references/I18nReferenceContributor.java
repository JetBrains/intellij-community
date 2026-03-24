// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.hints.declarative.InlayHintsProviderExtensionBean;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.ide.TypeNameEP;
import com.intellij.notification.impl.NotificationGroupEP;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationStarterEP;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SchemeConvertorEPBase;
import com.intellij.openapi.options.advanced.AdvancedSettingBean;
import com.intellij.patterns.DomPatterns;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.patterns.XmlTagPattern;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.IconDescriptionBundleEP;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Actions;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.Separator;
import org.jetbrains.idea.devkit.dom.Synonym;
import org.jetbrains.idea.devkit.util.PsiUtil;

import static com.intellij.patterns.XmlPatterns.or;
import static com.intellij.patterns.XmlPatterns.xmlAttribute;
import static com.intellij.patterns.XmlPatterns.xmlAttributeValue;
import static com.intellij.patterns.XmlPatterns.xmlTag;

final class I18nReferenceContributor extends PsiReferenceContributor {
  private static final String INTENTION_ACTION_TAG = "intentionAction";
  private static final String INTENTION_ACTION_BUNDLE_TAG = "bundleName";
  private static final String SEPARATOR_TAG = "separator";
  private static final String SYNONYM_TAG = "synonym";

  private static class Holder {
    private static final String APP_STARTER_EP = ApplicationStarterEP.class.getName();
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
    private static final String POLY_SYMBOLS_INSPECTION_TOOL_MAPPING_EP =
      "com.intellij.polySymbols.inspections.impl.PolySymbolsInspectionToolMappingEP";
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registerKeyProviders(registrar);
    registerBundleNameProviders(registrar);
    registerLiveTemplateSetXml(registrar);
  }

  private static void registerKeyProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"key"}, Holder.APP_STARTER_EP),
      new PropertyKeyReferenceProvider(false, null, null)
    );

    registrar.registerReferenceProvider(
      extensionAttributePattern(
        new String[]{"key", "groupKey"},
        Holder.CONFIGURABLE_EP, Holder.INSPECTION_EP, Holder.NOTIFICATION_GROUP_EP
      ),
      new PropertyKeyReferenceProvider(false, "groupKey", "groupBundle")
    );

    registrar.registerReferenceProvider(
      nestedExtensionAttributePattern("key", "groupKey"),
      new PropertyKeyReferenceProvider(false, "groupKey", "groupBundle")
    );

    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"groupPathKey"}, Holder.INSPECTION_EP),
      new PropertyKeyReferenceProvider(false, "groupPathKey", "groupBundle")
    );

    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"nameKey"}, Holder.SCHEME_CONVERTER_EP),
      new PropertyKeyReferenceProvider(false, "nameKey", "nameBundle")
    );

    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"displayNameKey"}, Holder.GROUP_CONFIGURABLE_EP),
      new PropertyKeyReferenceProvider(false, "displayNameKey", null) {
        @Override
        protected String getFallbackBundleName() {
          return OptionsBundle.BUNDLE;
        }
      }
    );

    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"descriptionKey"}, Holder.GROUP_CONFIGURABLE_EP),
      new PropertyKeyReferenceProvider(false, "descriptionKey", null) {
        @Override
        protected String getFallbackBundleName() {
          return OptionsBundle.BUNDLE;
        }
      }
    );

    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"resourceKey"}, Holder.TYPE_NAME_EP),
      new PropertyKeyReferenceProvider(false, "resourceKey", "resourceBundle")
    );

    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"displayName"}, Holder.SPRING_TOOL_WINDOW_CONTENT),
      new PropertyKeyReferenceProvider(false, "displayName", "bundle")
    );

    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"messageKey"}, Holder.POLY_SYMBOLS_INSPECTION_TOOL_MAPPING_EP),
      new PropertyKeyReferenceProvider(false, "messageKey", "bundleName")
    );

    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"nameKey"}, Holder.DECLARATIVE_INLAY_PROVIDER_EP),
      new PropertyKeyReferenceProvider(false, "nameKey", null)
    );

    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"descriptionKey"}, Holder.DECLARATIVE_INLAY_PROVIDER_EP),
      new PropertyKeyReferenceProvider(false, "descriptionKey", null)
    );

    registrar.registerReferenceProvider(
      nestedTagExtensionAttributePattern("nameKey"),
      new PropertyKeyReferenceProvider(false, "nameKey", null)
    );

    registrar.registerReferenceProvider(
      nestedTagExtensionAttributePattern("descriptionKey"),
      new PropertyKeyReferenceProvider(false, "descriptionKey", null)
    );

    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"id"}, Holder.ADVANCED_SETTINGS_EP)
        .andNot(xmlAttributeValue().withSuperParent(2, xmlTag().withChild(xmlAttribute("titleKey")))),
      new PropertyKeyReferenceProvider(false, "id", null) {
        @Override
        protected @NotNull String getFinalKeyValue(String keyValue) {
          return MessageBundleReferenceContributor.ADVANCED_SETTING + keyValue;
        }

        @Override
        protected String getFallbackBundleName() {
          return ApplicationBundle.BUNDLE;
        }
      }
    );
    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"titleKey"}, Holder.ADVANCED_SETTINGS_EP),
      new PropertyKeyReferenceProvider(false, "titleKey", null) {
        @Override
        protected String getFallbackBundleName() {
          return ApplicationBundle.BUNDLE;
        }
      }
    );
    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"groupKey"}, Holder.ADVANCED_SETTINGS_EP),
      new PropertyKeyReferenceProvider(false, "groupKey", null) {
        @Override
        protected String getFallbackBundleName() {
          return ApplicationBundle.BUNDLE;
        }
      });
    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"descriptionKey"}, Holder.ADVANCED_SETTINGS_EP),
      new PropertyKeyReferenceProvider(false, "descriptionKey", null) {
        @Override
        protected String getFallbackBundleName() {
          return ApplicationBundle.BUNDLE;
        }
      }
    );
    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"trailingLabelKey"}, Holder.ADVANCED_SETTINGS_EP),
      new PropertyKeyReferenceProvider(false, "trailingLabelKey", null) {
        @Override
        protected String getFallbackBundleName() {
          return ApplicationBundle.BUNDLE;
        }
      }
    );

    registrar.registerReferenceProvider(
      xmlAttributeValue("key")
        .withSuperParent(2, or(DomPatterns.tagWithDom(SEPARATOR_TAG, Separator.class), DomPatterns.tagWithDom(SYNONYM_TAG, Synonym.class))),
      new PropertyKeyReferenceProvider(tag -> {
        var domElement = DomUtil.getDomElement(tag);
        if (domElement == null) return null;
        var actions = DomUtil.getParentOfType(domElement, Actions.class, true);
        return actions != null ? actions.getResourceBundle().getStringValue() : null;
      })
    );

    registrar.registerReferenceProvider(
      xmlTag().withLocalName("categoryKey").withParent(DomPatterns.tagWithDom(INTENTION_ACTION_TAG, Extension.class)),
      new PropertyKeyReferenceProvider(true, null, INTENTION_ACTION_BUNDLE_TAG)
    );
  }

  private static void registerBundleNameProviders(PsiReferenceRegistrar registrar) {
    var bundleReferenceProvider = new ResourceBundlePsiReferenceProvider();
    registrar.registerReferenceProvider(
      xmlTag().withLocalName("resource-bundle").withParent(DomPatterns.tagWithDom(IdeaPlugin.TAG_NAME, IdeaPlugin.class)),
      bundleReferenceProvider
    );
    registrar.registerReferenceProvider(
      xmlAttributeValue("resource-bundle").withSuperParent(2, DomPatterns.tagWithDom("actions", Actions.class)),
      bundleReferenceProvider
    );
    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"bundle"}, Holder.APP_STARTER_EP),
      bundleReferenceProvider
    );
    registrar.registerReferenceProvider(
      extensionAttributePattern(
        new String[]{"bundle", "groupBundle"},
        Holder.CONFIGURABLE_EP, Holder.INSPECTION_EP, Holder.GROUP_CONFIGURABLE_EP, Holder.NOTIFICATION_GROUP_EP,
        Holder.SPRING_TOOL_WINDOW_CONTENT, Holder.ADVANCED_SETTINGS_EP, Holder.DECLARATIVE_INLAY_PROVIDER_EP
      ),
      bundleReferenceProvider
    );
    registrar.registerReferenceProvider(
      nestedExtensionAttributePattern("bundle", "groupBundle"),
      bundleReferenceProvider
    );
    registrar.registerReferenceProvider(
      nestedTagExtensionAttributePattern("bundle"),
      bundleReferenceProvider
    );
    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"nameBundle"}, Holder.SCHEME_CONVERTER_EP),
      bundleReferenceProvider
    );
    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"resourceBundle"}, Holder.TYPE_NAME_EP, Holder.ICON_DESCRIPTION_BUNDLE_EP),
      bundleReferenceProvider
    );
    registrar.registerReferenceProvider(
      extensionAttributePattern(new String[]{"bundleName"}, Holder.POLY_SYMBOLS_INSPECTION_TOOL_MAPPING_EP),
      bundleReferenceProvider
    );
    registrar.registerReferenceProvider(
      xmlTag().withLocalName(INTENTION_ACTION_BUNDLE_TAG).withParent(DomPatterns.tagWithDom(INTENTION_ACTION_TAG, Extension.class)),
      bundleReferenceProvider
    );
  }

  private static XmlAttributeValuePattern extensionAttributePattern(String[] attributeNames, String... extensionPointClassNames) {
    return xmlAttributeValue(attributeNames)
      .inFile(DomPatterns.inDomFile(IdeaPlugin.class))
      .withSuperParent(2, extensionPointCapture(extensionPointClassNames));
  }

  // special case for nested EPs, ConfigurableEP#children
  private static XmlAttributeValuePattern nestedExtensionAttributePattern(String... attributeNames) {
    return xmlAttributeValue(attributeNames)
      .inFile(DomPatterns.inDomFile(IdeaPlugin.class))
      .withSuperParent(3, extensionPointCapture(Holder.CONFIGURABLE_EP));
  }

  // special case for nested tag in EPs
  private static XmlAttributeValuePattern nestedTagExtensionAttributePattern(String... attributeNames) {
    return xmlAttributeValue(attributeNames)
      .inFile(DomPatterns.inDomFile(IdeaPlugin.class))
      .withSuperParent(2, xmlTag().withLocalName("option"))
      .withSuperParent(3, extensionPointCapture(Holder.DECLARATIVE_INLAY_PROVIDER_EP));
  }

  private static XmlTagPattern.Capture extensionPointCapture(String... extensionPointClassNames) {
    return xmlTag().and(
      DomPatterns.withDom(DomPatterns.domElement(Extension.class).with(new PatternCondition<>("relevantEP") {
        @Override
        public boolean accepts(@NotNull Extension extension, ProcessingContext context) {
          var extensionPoint = extension.getExtensionPoint();
          if (extensionPoint == null) return false;

          var beanClass = extensionPoint.getBeanClass().getValue();
          for (var name : extensionPointClassNames) {
            if (InheritanceUtil.isInheritor(beanClass, name)) return true;
          }
          return false;
        }
      }))
    );
  }

  private static void registerLiveTemplateSetXml(PsiReferenceRegistrar registrar) {
    var templateTagCapture = xmlTag().withLocalName("template")
      .withParent(xmlTag().withLocalName("templateSet"))
      .with(new PatternCondition<>("pluginProject") {
        @Override
        public boolean accepts(@NotNull XmlTag tag, ProcessingContext context) {
          return PsiUtil.isPluginProject(tag.getProject());
        }
      });
    registrar.registerReferenceProvider(
      xmlAttributeValue("key").withSuperParent(2, templateTagCapture),
      new PropertyKeyReferenceProvider(tag -> tag.getAttributeValue("resource-bundle"))
    );
    registrar.registerReferenceProvider(
      xmlAttributeValue("resource-bundle").withSuperParent(2, templateTagCapture),
      new ResourceBundlePsiReferenceProvider()
    );
  }
}
