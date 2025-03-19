// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.patterns.VirtualFilePattern;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.patterns.PlatformPatterns.virtualFile;

final class MessageBundleReferenceContributor extends PsiReferenceContributor {
  private static final @NonNls String ACTION = "action.";
  private static final @NonNls String GROUP = "group.";
  private static final @NonNls String TEXT = ".text";
  private static final @NonNls String DESCRIPTION = ".description";
  private static final @NonNls String TRAILING_LABEL = ".trailingLabel";
  public static final @NonNls String ADVANCED_SETTING = "advanced.setting.";
  public static final @NonNls String BUNDLE_PROPERTIES = "Bundle.properties";

  private static final @NonNls String TOOLWINDOW_STRIPE_PREFIX = "toolwindow.stripe.";
  private static final @NonNls String EXPORTABLE_PREFIX = "exportable.";
  private static final @NonNls String EXPORTABLE_SUFFIX = ".presentable.name";

  private static final @NonNls String PLUGIN = "plugin.";

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(PropertyKeyImpl.class).inVirtualFile(bundleFile()),
      new PsiReferenceProvider() {

        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
          if (!(element instanceof PropertyKeyImpl)) return PsiReference.EMPTY_ARRAY;
          if (!isPluginProject(element)) return PsiReference.EMPTY_ARRAY;

          String text = ((PropertyKeyImpl)element).getText();
          return JBIterable.of(
            createActionOrGroupIdReference(element, text),
            createToolwindowIdReference(element, text),
            createExportableIdReference(element, text),
            createPluginIdReference(element, text),
            createAdvancedSettingReference(element, text)
          ).filter(Objects::nonNull).toArray(PsiReference.EMPTY_ARRAY);
        }

        private static @Nullable PsiReference createActionOrGroupIdReference(@NotNull PsiElement element, String text) {
          if (!isActionOrGroupKey(text)) return null;

          final int dotAfterPrefix = text.indexOf('.');
          if (dotAfterPrefix == -1) return null;
          final int prefixEndIdx = dotAfterPrefix + 1;

          final int dotBeforeSuffix = text.lastIndexOf('.');
          if (dotBeforeSuffix == -1) return null;
          if (dotBeforeSuffix <= prefixEndIdx) return null;

          String id = text.substring(prefixEndIdx, dotBeforeSuffix);
          String prefix = text.substring(0, prefixEndIdx);

          ThreeState isAction = prefix.equals(ACTION) ? ThreeState.YES : ThreeState.NO;
          return new ActionOrGroupIdReference(element, TextRange.allOf(id).shiftRight(prefix.length()), id, isAction);
        }

        private static @Nullable PsiReference createToolwindowIdReference(@NotNull PsiElement element, String text) {
          if (!isToolwindowKey(text)) return null;

          String id = StringUtil.notNullize(StringUtil.substringAfter(text, TOOLWINDOW_STRIPE_PREFIX)).replace('_', ' ');
          return new ToolwindowIdReference(element, id);
        }

        private static @Nullable PsiReference createExportableIdReference(@NotNull PsiElement element, String text) {
          if (!isExportableKey(text)) return null;

          String id = text.replace(EXPORTABLE_PREFIX, "").replace(EXPORTABLE_SUFFIX, "");
          return new ExportableIdReference(element, id);
        }

        private static @Nullable PsiReference createPluginIdReference(@NotNull PsiElement element, String text) {
          if (!isPluginDescriptionKey(text)) return null;

          String id = StringUtil.substringAfter(StringUtil.notNullize(StringUtil.substringBefore(text, DESCRIPTION)), PLUGIN);
          return new PluginIdReference(element, id);
        }

        private static @Nullable PsiReference createAdvancedSettingReference(@NotNull PsiElement element, String text) {
          if (!isAdvancedSettingKey(text)) return null;

          String s = StringUtil.notNullize(StringUtil.substringAfter(text, ADVANCED_SETTING));
          String id = s.endsWith(DESCRIPTION) ? StringUtil.trimEnd(s, DESCRIPTION) :
                      (s.endsWith(TRAILING_LABEL) ? StringUtil.trimEnd(s, TRAILING_LABEL) : s);
          TextRange range = TextRange.allOf(id).shiftRight(ADVANCED_SETTING.length());
          return new AdvancedSettingsIdContributor.AdvancedSettingReference(element, range);
        }
      });
  }

  private static VirtualFilePattern bundleFile() {
    return virtualFile().ofType(PropertiesFileType.INSTANCE).withName(StandardPatterns.string().endsWith(BUNDLE_PROPERTIES));
  }

  private static boolean isPluginProject(PsiElement property) {
    return PsiUtil.isPluginProject(property.getProject());
  }

  private static boolean isActionOrGroupKey(String name) {
    return (name.startsWith(ACTION) || name.startsWith(GROUP)) &&
           (name.endsWith(TEXT) || name.endsWith(DESCRIPTION));
  }

  private static boolean isExportableKey(String name) {
    return name.startsWith(EXPORTABLE_PREFIX) && name.endsWith(EXPORTABLE_SUFFIX);
  }

  private static boolean isToolwindowKey(String name) {
    return name.startsWith(TOOLWINDOW_STRIPE_PREFIX);
  }

  private static boolean isPluginDescriptionKey(String name) {
    return name.startsWith(PLUGIN) && name.endsWith(DESCRIPTION);
  }

  private static boolean isAdvancedSettingKey(String name) {
    return name.startsWith(ADVANCED_SETTING);
  }


  private static class PluginIdReference extends PsiPolyVariantReferenceBase<PsiElement> {

    private PluginIdReference(@NotNull PsiElement element, String id) {
      super(element, TextRange.allOf(id).shiftRight(PLUGIN.length()));
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
      List<PsiElement> psiElements = new SmartList<>();
      final String id = getValue();

      final Project project = getElement().getProject();
      for (IdeaPlugin plugin : getRelevantPlugins()) {
        if (id.equals(plugin.getPluginId())) {
          final DomTarget target = DomTarget.getTarget(plugin);
          assert target != null;
          psiElements.add(PomService.convertToPsi(project, target));
        }
      }
      return PsiElementResolveResult.createResults(psiElements);
    }

    @Override
    public Object @NotNull [] getVariants() {
      return ContainerUtil.map2Array(getRelevantPlugins(), LookupElement.class,
                                     plugin -> LookupElementBuilder.create(Objects.requireNonNull(plugin.getPluginId()))
                                       .withPsiElement(plugin.getXmlElement())
                                       .withTailText(" " + StringUtil.notNullize(plugin.getName().getValue()))
                                       .withIcon(ElementPresentationManager.getIcon(plugin)));
    }

    private @Unmodifiable Collection<IdeaPlugin> getRelevantPlugins() {
      return ContainerUtil.filter(DescriptorUtil.getPlugins(getElement().getProject(), getElement().getResolveScope()),
                                  plugin -> plugin.hasRealPluginId() && Boolean.TRUE != plugin.getImplementationDetail().getValue());
    }
  }


  private static class ToolwindowIdReference extends ExtensionReferenceBase {

    private ToolwindowIdReference(@NotNull PsiElement element, String id) {
      super(element, TextRange.allOf(id).shiftRight(TOOLWINDOW_STRIPE_PREFIX.length()));
    }

    @Override
    protected String getExtensionPointFqn() {
      return "com.intellij.toolWindow";
    }

    @Override
    public @InspectionMessage @NotNull String getUnresolvedMessagePattern() {
      return DevKitBundle.message("message.bundle.convert.toolwindow.id.cannot.resolve", getValue());
    }

    @Override
    protected @NotNull @NlsSafe String getResolveValue() {
      return super.getResolveValue().replace('_', ' ');
    }

    @Override
    public Object @NotNull [] getVariants() {
      final List<LookupElement> variants = Collections.synchronizedList(new SmartList<>());
      processCandidates(extension -> {
        final GenericAttributeValue<String> id = extension.getId();
        if (id == null || extension.getXmlElement() == null) return true;

        final String value = id.getStringValue();
        if (value == null) return true;

        variants.add(LookupElementBuilder.create(extension.getXmlElement(), value.replace(' ', '_'))
                       .withTypeText(getAttributeValue(extension, "factoryClass")));
        return true;
      });
      return variants.toArray(LookupElement.EMPTY_ARRAY);
    }
  }


  private static class ExportableIdReference extends PsiReferenceBase.Poly<PsiElement> {

    private ExportableIdReference(PsiElement element, String id) {
      super(element, TextRange.allOf(id).shiftRight(EXPORTABLE_PREFIX.length()), false);
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
      String id = getValue();

      List<PsiElement> resolves = new SmartList<>();
      processStateAnnoClasses((psiClass, name) -> {
        if (StringUtil.equals(id, name)) {
          resolves.add(psiClass);
        }
        return true;
      });

      return PsiElementResolveResult.createResults(resolves);
    }

    @Override
    public Object @NotNull [] getVariants() {
      List<LookupElement> variants = new SmartList<>();
      PairProcessor<PsiClass, String> variantProcessor = (psiClass, id) -> {
        variants.add(LookupElementBuilder.create(psiClass, id)
                       .withTypeText(psiClass.getQualifiedName()));
        return true;
      };
      processStateAnnoClasses(variantProcessor);
      return variants.toArray();
    }

    private void processStateAnnoClasses(PairProcessor<PsiClass, String> processor) {
      final Project project = myElement.getProject();
      final GlobalSearchScope searchScope = IntelliJProjectUtil.isIntelliJPlatformProject(project) ?
                                            GlobalSearchScopesCore.projectProductionScope(project) : getElement().getResolveScope();
      final PsiClass statePsiClass = JavaPsiFacade.getInstance(project).findClass(State.class.getName(), searchScope);
      if (statePsiClass == null) {
        return;
      }

      final Query<PsiClass> query = AnnotatedElementsSearch.searchPsiClasses(statePsiClass, searchScope);
      query.forEach(psiClass -> {
        final PsiAnnotation stateAnnotation = AnnotationUtil.findAnnotation(psiClass, true, State.class.getName());
        assert stateAnnotation != null : psiClass;

        if (AnnotationUtil.findDeclaredAttribute(stateAnnotation, "presentableName") != null) return true;

        final String nameAttributeValue = AnnotationUtil.getDeclaredStringAttributeValue(stateAnnotation, "name");
        if (StringUtil.isEmpty(nameAttributeValue)) return true;

        return processor.process(psiClass, nameAttributeValue);
      });
    }
  }


  static final class ImplicitUsageProvider implements ImplicitPropertyUsageProvider {

    private static final @NonNls String ICON_TOOLTIP_PREFIX = "icon.";
    private static final @NonNls String ICON_TOOLTIP_SUFFIX = ".tooltip";

    @Override
    public boolean isUsed(@NotNull Property property) {
      PsiFile file = property.getContainingFile();
      String fileName = file.getName();
      if (!fileName.endsWith(BUNDLE_PROPERTIES)) return false;

      String name = property.getName();
      if (name == null) return false;

      if (isActionOrGroupKey(name) ||
          isExportableKey(name) ||
          isToolwindowKey(name) ||
          isPluginDescriptionKey(name) ||
          isAdvancedSettingKey(name)) {
        PsiElement key = property.getFirstChild();
        PsiReference[] references = key == null ? PsiReference.EMPTY_ARRAY : key.getReferences();

        boolean hasResolve = ContainerUtil.exists(references, reference -> {
          boolean unresolved = reference instanceof PsiPolyVariantReference
                               ? ((PsiPolyVariantReference)reference).multiResolve(false).length == 0
                               : reference.resolve() == null;
          return !unresolved;
        });
        return hasResolve && isPluginProject(property);
      }

      return isIconTooltipKey(name) && isPluginProject(property);
    }

    private static boolean isIconTooltipKey(String name) {
      return name.startsWith(ICON_TOOLTIP_PREFIX) && name.endsWith(ICON_TOOLTIP_SUFFIX);
    }
  }
}
