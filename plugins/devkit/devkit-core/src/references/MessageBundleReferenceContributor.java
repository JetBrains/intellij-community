// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.unused.ImplicitPropertyUsageProvider;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.patterns.VirtualFilePattern;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.patterns.PlatformPatterns.virtualFile;

public class MessageBundleReferenceContributor extends PsiReferenceContributor {

  @NonNls private static final String ACTION = "action.";
  @NonNls private static final String GROUP = "group.";
  @NonNls private static final String TEXT = ".text";
  @NonNls private static final String DESC = ".description";
  @NonNls private static final String BUNDLE_PROPERTIES = "Bundle.properties";

  @NonNls private static final String TOOLWINDOW_STRIPE_PREFIX = "toolwindow.stripe.";
  @NonNls private static final String EXPORTABLE_PREFIX = "exportable.";
  @NonNls private static final String EXPORTABLE_SUFFIX = ".presentable.name";

  public static final PsiElementResolveResult[] EMPTY_RESOLVE_RESULT = new PsiElementResolveResult[0];

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(PropertyKeyImpl.class).inVirtualFile(bundleFile()),
      new PsiReferenceProvider() {

        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
          if (!(element instanceof PropertyKeyImpl)) return PsiReference.EMPTY_ARRAY;
          if (!PsiUtil.isPluginProject(element.getProject())) return PsiReference.EMPTY_ARRAY;

          String text = ((PropertyKeyImpl)element).getText();
          return JBIterable.of(
            createActionReference(element, text, ACTION, TEXT),
            createActionReference(element, text, ACTION, DESC),
            createActionReference(element, text, GROUP, TEXT),
            createActionReference(element, text, GROUP, DESC),
            createToolwindowIdReference(element, text),
            createExportableIdReference(element, text)
          ).filter(Objects::nonNull).toArray(PsiReference.EMPTY_ARRAY);
        }

        @Nullable
        private PsiReference createActionReference(@NotNull PsiElement element, String text, String prefix, String suffix) {
          if (!text.startsWith(prefix) || !text.endsWith(suffix)) return null;

          String id = text.replace(prefix, "").replace(suffix, "");
          return new DevKitActionReference(id, prefix, element);
        }

        @Nullable
        private PsiReference createToolwindowIdReference(@NotNull PsiElement element, String text) {
          if (!text.startsWith(TOOLWINDOW_STRIPE_PREFIX)) return null;

          String id = StringUtil.notNullize(StringUtil.substringAfter(text, TOOLWINDOW_STRIPE_PREFIX)).replace('_', ' ');
          return new ToolwindowIdReference(element, id);
        }

        @Nullable
        private PsiReference createExportableIdReference(@NotNull PsiElement element, String text) {
          if (!text.startsWith(EXPORTABLE_PREFIX) || !text.endsWith(EXPORTABLE_SUFFIX)) return null;

          String id = text.replace(EXPORTABLE_PREFIX, "").replace(EXPORTABLE_SUFFIX, "");
          return new ExportableIdReference(element, id);
        }
      });
  }

  private static VirtualFilePattern bundleFile() {
    return virtualFile().ofType(PropertiesFileType.INSTANCE).withName(StandardPatterns.string().endsWith(BUNDLE_PROPERTIES));
  }

  private static final class DevKitActionReference extends PsiPolyVariantReferenceBase<PsiElement> {
    private final String myId;
    private final boolean myIsAction;

    private DevKitActionReference(String id, String prefix, @NotNull PsiElement element) {
      super(element, TextRange.allOf(id).shiftRight(prefix.length()));
      myIsAction = prefix.equals(ACTION);
      myId = id;
    }

    @NotNull
    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
      Project project = getElement().getProject();

      CommonProcessors.CollectUniquesProcessor<ActionOrGroup> processor = new CommonProcessors.CollectUniquesProcessor<>();
      if (myIsAction) {
        IdeaPluginRegistrationIndex.processAction(project, myId, ProjectScope.getContentScope(project), processor);
      }
      else {
        IdeaPluginRegistrationIndex.processGroup(project, myId, ProjectScope.getContentScope(project), processor);
      }

      return JBIterable
        .from(processor.getResults())
        .map(actionOrGroup -> {
          final DomTarget target = DomTarget.getTarget(actionOrGroup);
          return target == null ? null : new PsiElementResolveResult(PomService.convertToPsi(project, target));
        }).filter(Objects::nonNull).toArray(EMPTY_RESOLVE_RESULT);
    }
  }


  private static class ToolwindowIdReference extends ExtensionPointReferenceBase {

    private ToolwindowIdReference(@NotNull PsiElement element, String id) {
      super(element, TextRange.allOf(id).shiftRight(TOOLWINDOW_STRIPE_PREFIX.length()));
    }

    @Override
    protected String getExtensionPointClassname() {
      return ToolWindowEP.class.getName();
    }

    @Override
    protected GenericAttributeValue<?> getNameElement(Extension extension) {
      return extension.getId();
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
      final GlobalSearchScope searchScope = PsiUtil.isIdeaProject(project) ?
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


  public static class ImplicitUsageProvider extends ImplicitPropertyUsageProvider {

    @NonNls public static final String ICON_TOOLTIP_PREFIX = "icon.";
    @NonNls public static final String ICON_TOOLTIP_SUFFIX = ".tooltip";

    @Override
    protected boolean isUsed(@NotNull Property property) {
      PsiFile file = property.getContainingFile();
      String fileName = file.getName();
      if (!fileName.endsWith(BUNDLE_PROPERTIES)) return false;
      String name = property.getName();
      if (name == null) return false;

      if ((name.startsWith(ACTION) || name.startsWith(GROUP)) &&
          (name.endsWith(TEXT) || name.endsWith(DESC)) ||
          (name.startsWith(EXPORTABLE_PREFIX) && name.endsWith(EXPORTABLE_SUFFIX)) ||
          name.startsWith(TOOLWINDOW_STRIPE_PREFIX)) {
        PsiElement key = property.getFirstChild();
        PsiReference[] references = key == null ? PsiReference.EMPTY_ARRAY : key.getReferences();
        return ContainerUtil.exists(references, reference -> reference.resolve() != null);
      }

      return name.startsWith(ICON_TOOLTIP_PREFIX) && name.endsWith(ICON_TOOLTIP_SUFFIX);
    }
  }
}
