// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.application.options.RegistryManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.PropertyReferenceBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.inspections.RegistryPropertiesAnnotator;
import org.jetbrains.uast.UExpression;

import java.util.Collections;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.patterns.uast.UastPatterns.injectionHostUExpression;

final class RegistryKeyIdReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    UastReferenceRegistrar
      .registerUastReferenceProvider(registrar,
                                     injectionHostUExpression().methodCallParameter(0, psiMethod()
                                       .withName(string().oneOf("get", "is", "intValue", "doubleValue", "stringValue", "getColor"))
                                       .definedInClass(PsiJavaPatterns.psiClass().withQualifiedName(string().oneOf(
                                         Registry.class.getName(),
                                         RegistryManager.class.getName()
                                       )))),
                                     new UastInjectionHostReferenceProvider() {
                                       @Override
                                       public boolean acceptsTarget(@NotNull PsiElement target) {
                                         return PropertyReferenceBase.isPropertyPsi(target);
                                       }

                                       @Override
                                       public PsiReference @NotNull [] getReferencesForInjectionHost(@NotNull UExpression uExpression,
                                                                                                     @NotNull PsiLanguageInjectionHost host,
                                                                                                     @NotNull ProcessingContext context) {
                                         return new PsiReference[]{new RegistryKeyIdReference(host)};
                                       }
                                     }, PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }


  private static final class RegistryKeyIdReference extends ExtensionPointReferenceBase {

    private RegistryKeyIdReference(@NotNull PsiElement element) {
      super(element);
    }

    @Override
    protected String getExtensionPointFqn() {
      return "com.intellij.registryKey";
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return DevKitBundle.message("code.convert.registry.key.cannot.resolve", getValue());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected @Nullable GenericAttributeValue<String> getNameElement(Extension extension) {
      return (GenericAttributeValue<String>)getAttribute(extension, "key");
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      final PropertiesFile file = getRegistryPropertiesFile();
      if (file != null) {
        final IProperty propertyKey = file.findPropertyByKey(getValue());
        if (propertyKey != null) {
          return propertyKey.getPsiElement();
        }
      }

      return super.resolve();
    }

    @Override
    public Object @NotNull [] getVariants() {
      final PropertiesFile registryProperties = getRegistryPropertiesFile();
      if (registryProperties == null) {
        return EMPTY_ARRAY;
      }

      final List<LookupElement> variants = Collections.synchronizedList(new SmartList<>());
      processCandidates(extension -> {
        final GenericAttributeValue<String> nameElement = getNameElement(extension);
        if (nameElement == null) return true;

        final String key = nameElement.getStringValue();
        if (key == null || extension.getXmlElement() == null) return true;

        final boolean requireRestart = "true".equals(getAttributeValue(extension, "restartRequired"));
        final String description = " " + StringUtil.notNullize(getAttributeValue(extension, "description"),
                                                               DevKitBundle.message("code.convert.registry.key.no.description"));
        final String defaultValue = StringUtil.notNullize(getAttributeValue(extension, "defaultValue"),
                                                          DevKitBundle.message("code.convert.registry.key.no.default.value"));

        variants.add(LookupElementBuilder.create(extension.getXmlElement(), key)
                       .withIcon(requireRestart ? AllIcons.Nodes.PluginRestart : AllIcons.Nodes.Plugin)
                       .withTailText(description, true)
                       .withTypeText(defaultValue));
        return true;
      });


      for (IProperty property : registryProperties.getProperties()) {
        final String key = property.getKey();
        if (key == null || RegistryPropertiesAnnotator.isImplicitUsageKey(key)) continue;

        final boolean requireRestart =
          registryProperties.findPropertyByKey(key + RegistryPropertiesAnnotator.RESTART_REQUIRED_SUFFIX) != null;

        final IProperty descriptionKey = registryProperties.findPropertyByKey(key + RegistryPropertiesAnnotator.DESCRIPTION_SUFFIX);
        String description = descriptionKey != null ? " " + cleanupDescription(descriptionKey.getUnescapedValue()) : "";
        variants.add(LookupElementBuilder.create(property.getPsiElement(), key)
                       .withIcon(requireRestart ? AllIcons.Nodes.PluginRestart : AllIcons.Nodes.Plugin)
                       .withTailText(description, true)
                       .withTypeText(property.getValue()));
      }

      return variants.toArray(LookupElement.EMPTY_ARRAY);
    }

    private static String cleanupDescription(String description) {
      return StringUtil.strip(description, ch -> ch != '\n' && ch != '\r');
    }

    @Nullable
    private PropertiesFile getRegistryPropertiesFile() {
      Module module = ModuleUtilCore.findModuleForPsiElement(getElement());
      if (module == null) return null;

      final PropertiesReferenceManager propertiesReferenceManager = PropertiesReferenceManager.getInstance(myElement.getProject());
      return ContainerUtil.getFirstItem(propertiesReferenceManager.findPropertiesFiles(module, Registry.REGISTRY_BUNDLE));
    }
  }
}
