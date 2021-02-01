// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.paths.PathReference;
import com.intellij.openapi.paths.PathReferenceManager;
import com.intellij.openapi.paths.PathReferenceProvider;
import com.intellij.openapi.paths.StaticPathReferenceProvider;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.converters.PathReferenceConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.*;

public class DependencyConfigFileConverter extends PathReferenceConverter {

  private static final PathReferenceProvider ourProvider = new StaticPathReferenceProvider(null) {

    @Override
    public boolean createReferences(@NotNull final PsiElement psiElement,
                                    int offset,
                                    String text,
                                    @NotNull List<? super PsiReference> references,
                                    boolean soft) {
      FileReferenceSet set = new FileReferenceSet(text, psiElement, offset, null,
                                                  true, true,
                                                  new FileType[]{XmlFileType.INSTANCE}) {

        private final Condition<PsiFileSystemItem> PLUGIN_XML_CONDITION = item -> !item.isDirectory() &&
                                                                              !item.equals(getContainingFile()) &&
                                                                              (item instanceof XmlFile && DescriptorUtil.isPluginXml((PsiFile)item)) &&
                                                                              !isAlreadyUsed((XmlFile)item);

        private boolean isAlreadyUsed(final XmlFile xmlFile) {
          final PsiFile file = getContainingFile();
          if (!(file instanceof XmlFile)) return false;
          final IdeaPlugin ideaPlugin = DescriptorUtil.getIdeaPlugin((XmlFile)file);
          if (ideaPlugin == null) return false;
          return !ContainerUtil.process(ideaPlugin.getDepends(), dependency -> {
            final GenericAttributeValue<PathReference> configFileAttribute = dependency.getConfigFile();
            if (!DomUtil.hasXml(configFileAttribute)) return true;
            final PathReference pathReference = configFileAttribute.getValue();
            if (pathReference == null) return true;
            return !xmlFile.equals(pathReference.resolve());
          });
        }

        @NotNull
        @Override
        public Collection<PsiFileSystemItem> computeDefaultContexts() {
          final PsiFile containingFile = getContainingFile();
          if (containingFile == null) {
            return Collections.emptyList();
          }

          final Module module = ModuleUtilCore.findModuleForPsiElement(getElement());
          if (module == null) {
            return Collections.emptyList();
          }

          final Set<VirtualFile> roots = new HashSet<>();
          final VirtualFile parent = containingFile.getVirtualFile().getParent();
          roots.add(parent);

          for (VirtualFile sourceRoot : ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.PRODUCTION)) {
            roots.add(sourceRoot.findChild("META-INF"));
          }
          return toFileSystemItems(roots);
        }

        @Override
        protected boolean isSoft() {
          return true;
        }

        @Override
        protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
          return PLUGIN_XML_CONDITION;
        }
      };
      Collections.addAll(references, set.getAllReferences());
      return true;
    }
  };

  @Override
  public PathReference fromString(@Nullable String s, ConvertContext context) {
    final XmlElement element = context.getReferenceXmlElement();
    final Module module = context.getModule();
    if (s == null || element == null || module == null) {
      return null;
    }
    return PathReferenceManager.getInstance().getCustomPathReference(s, module, element, ourProvider);
  }

  @Override
  public PsiReference @NotNull [] createReferences(@NotNull PsiElement psiElement, boolean soft) {
    return PathReferenceManager.getInstance().createCustomReferences(psiElement,
                                                                     soft,
                                                                     ourProvider);
  }
}
