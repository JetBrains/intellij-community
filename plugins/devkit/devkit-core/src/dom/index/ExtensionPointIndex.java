// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.*;
import java.util.function.Supplier;

/**
 * Index of EP declarations via {@link ExtensionPoint#getEffectiveQualifiedName}.
 *
 * @see ExtensionPointClassIndex
 */
public class ExtensionPointIndex extends PluginXmlIndexBase<String, Integer> {

  private static final ID<String, Integer> NAME = ID.create("devkit.ExtensionPointIndex");

  @NotNull
  @Override
  public ID<String, Integer> getName() {
    return NAME;
  }

  @Override
  protected Map<String, Integer> performIndexing(IdeaPlugin plugin) {
    Map<String, Integer> result = new HashMap<>();
    indexExtensionPoints(plugin, point -> result.put(point.getEffectiveQualifiedName(), point.getXmlTag().getTextOffset()));
    return result;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public DataExternalizer<Integer> getValueExternalizer() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @NotNull
  public static List<ExtensionPoint> getExtensionPointCandidates(Project project, GlobalSearchScope scope) {
    CommonProcessors.CollectProcessor<String> epNamesProcessor = new CommonProcessors.CollectProcessor<>();
    FileBasedIndex.getInstance().processAllKeys(NAME, epNamesProcessor, scope, null);

    List<ExtensionPoint> result = new ArrayList<>();
    for (String epName : epNamesProcessor.getResults()) {
      ContainerUtil.addIfNotNull(result, findExtensionPoint(project, scope, epName));
    }
    return result;
  }

  @Nullable
  public static ExtensionPoint findExtensionPoint(Module module, String fqn) {
    return findExtensionPoint(module.getProject(), GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false), fqn);
  }

  @Nullable
  public static ExtensionPoint findExtensionPoint(Project project, GlobalSearchScope scope, String fqn) {
    Ref<ExtensionPoint> result = Ref.create();
    FileBasedIndex.getInstance().processValues(NAME, fqn, null, (file, value) -> {
      final PsiManager psiManager = PsiManager.getInstance(project);
      final DomManager domManager = DomManager.getDomManager(project);

      result.set(getExtensionPointDom(psiManager, domManager, file, value));
      return false;
    }, scope);
    return result.get();
  }

  public static Map<String, Supplier<ExtensionPoint>> getExtensionPoints(Project project, Set<VirtualFile> files, String epPrefix) {
    Map<String, Supplier<ExtensionPoint>> result = new HashMap<>();

    final PsiManager psiManager = PsiManager.getInstance(project);
    final DomManager domManager = DomManager.getDomManager(project);
    for (VirtualFile file : files) {
      final Map<String, Integer> data = FileBasedIndex.getInstance().getFileData(NAME, file, project);
      if (data.isEmpty()) continue;

      for (Map.Entry<String, Integer> entry : data.entrySet()) {
        final String qualifiedName = entry.getKey();
        if (!StringUtil.startsWith(qualifiedName, epPrefix)) continue;

        result.put(qualifiedName, () -> {
          return getExtensionPointDom(psiManager, domManager, file, entry.getValue());
        });
      }
    }
    return result;
  }

  @Nullable
  static ExtensionPoint getExtensionPointDom(PsiManager psiManager,
                                             DomManager domManager,
                                             VirtualFile file,
                                             int offset) {
    PsiFile psiFile = psiManager.findFile(file);
    if (!(psiFile instanceof XmlFile)) return null;

    PsiElement psiElement = psiFile.findElementAt(offset);
    XmlTag xmlTag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);
    final DomElement domElement = domManager.getDomElement(xmlTag);
    return ObjectUtils.tryCast(domElement, ExtensionPoint.class);
  }

  static void indexExtensionPoints(IdeaPlugin plugin, Consumer<? super ExtensionPoint> consumer) {
    for (DomElement points : getChildrenWithoutIncludes(plugin, "extensionPoints")) {
      for (DomElement point : getChildrenWithoutIncludes(points, "extensionPoint")) {
        ExtensionPoint extensionPoint = (ExtensionPoint)point;
        consumer.consume(extensionPoint);
      }
    }
  }
}
