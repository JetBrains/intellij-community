// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex;

import java.util.Objects;

import static com.intellij.patterns.PlatformPatterns.virtualFile;

public class DevKitActionOrGroupIdReferenceContributor extends PsiReferenceContributor {
  @NonNls public static final String ACTION = "action.";
  @NonNls public static final String GROUP = "group.";
  @NonNls public static final String TEXT = ".text";
  @NonNls public static final String DESC = ".description";
  public static final PsiElementResolveResult[] EMPTY_RESOLVE_RESULT = new PsiElementResolveResult[0];

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(PropertyKeyImpl.class)
        .inVirtualFile(virtualFile().ofType(
          PropertiesFileType.INSTANCE).withName(StandardPatterns.string().endsWith("Bundle.properties"))),
      new PsiReferenceProvider() {
        @NotNull
        @Override
        public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                     @NotNull ProcessingContext context) {
          if (!(element instanceof PropertyKeyImpl)) return PsiReference.EMPTY_ARRAY;

          String text = ((PropertyKeyImpl)element).getText();
          return JBIterable.of(
            createRef(element, text, ACTION, TEXT),
            createRef(element, text, ACTION, DESC),
            createRef(element, text, GROUP, TEXT),
            createRef(element, text, ACTION, DESC)).filter(Objects::nonNull).toArray(PsiReference.EMPTY_ARRAY);
        }

        @Nullable
        private PsiReference createRef(@NotNull PsiElement element, String text, String prefix, String suffix) {
          if (!text.startsWith(prefix) || !text.endsWith(suffix)) return null;

          String id = text.replace(prefix, "").replace(suffix, "");
          return new DevKitActionReference(id, prefix, element);
        }
      });
  }

  private static class DevKitActionReference extends PsiPolyVariantReferenceBase<PsiElement> {
    private final String myId;
    private final boolean myIsAction;

    private DevKitActionReference(String id, String prefix, @NotNull PsiElement element) {
      super(element, TextRange.allOf(id).shiftRight(prefix.length()));
      myIsAction = prefix.equals(ACTION);
      myId = id;
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
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
        .map(a -> {
          XmlElement xml = a.getId().getXmlAttributeValue();
          return xml == null ? null : new PsiElementResolveResult(xml);
        }).filter(Objects::nonNull).toArray(EMPTY_RESOLVE_RESULT);
    }
  }
}
