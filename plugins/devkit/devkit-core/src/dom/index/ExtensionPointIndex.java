// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ObjectUtils;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.impl.AbstractCollectionChildDescription;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.*;
import java.util.function.Supplier;

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
    for (DomElement points : getChildrenWithoutIncludes(plugin, "extensionPoints")) {
      for (DomElement point : getChildrenWithoutIncludes(points, "extensionPoint")) {
        ExtensionPoint extensionPoint = (ExtensionPoint)point;
        result.put(extensionPoint.getEffectiveQualifiedName(), extensionPoint.getXmlTag().getTextOffset());
      }
    }
    return result;
  }

  // skip any xi:include
  private static List<? extends DomElement> getChildrenWithoutIncludes(DomElement parent, String tagName) {
    AbstractCollectionChildDescription collectionChildDescription =
      (AbstractCollectionChildDescription)parent.getGenericInfo().getCollectionChildDescription(tagName);
    DomInvocationHandler handler = Objects.requireNonNull(DomManagerImpl.getDomInvocationHandler(parent));
    return handler.getCollectionChildren(collectionChildDescription, false);
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
          PsiFile psiFile = psiManager.findFile(file);
          if (!(psiFile instanceof XmlFile)) return null;

          PsiElement psiElement = psiFile.findElementAt(entry.getValue());
          XmlTag xmlTag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);
          final DomElement domElement = domManager.getDomElement(xmlTag);
          return ObjectUtils.tryCast(domElement, ExtensionPoint.class);
        });
      }
    }
    return result;
  }
}
