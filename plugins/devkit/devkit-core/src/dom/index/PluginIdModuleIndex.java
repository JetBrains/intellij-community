// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.PluginModule;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.*;

public class PluginIdModuleIndex extends PluginXmlIndexBase<String, Void> {
  private static final ID<String, Void> NAME = ID.create("PluginIdModuleIndex");

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataExternalizer<Void> getValueExternalizer() {
    return VoidDataExternalizer.INSTANCE;
  }

  @Override
  protected Map<String, Void> performIndexing(IdeaPlugin plugin) {
    List<String> ids = new ArrayList<>();
    ids.add(StringUtil.notNullize(plugin.getPluginId()));
    for (DomElement module : getChildrenWithoutIncludes(plugin, "module")) {
      ContainerUtil.addIfNotNull(ids, ((PluginModule)module).getValue().getStringValue());
    }
    return ContainerUtil.newHashMap(ids, Collections.nCopies(ids.size(), null));
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public int getVersion() {
    return 2;
  }

  public static Collection<VirtualFile> getFiles(@NotNull Project project, @NotNull String idOrModule) {
    GlobalSearchScope scope = GlobalSearchScopesCore.projectProductionScope(project)
      .union(LibraryScopeCache.getInstance(project).getLibrariesOnlyScope());
    return FileBasedIndex.getInstance().getContainingFiles(NAME, idOrModule, scope);
  }

  public static List<IdeaPlugin> findPlugins(@NotNull DomElement place, @NotNull String idOrModule) {
    Project project = place.getManager().getProject();
    Collection<VirtualFile> vFiles = getFiles(project, idOrModule);
    return JBIterable.from(vFiles)
      .map(PsiManager.getInstance(project)::findFile)
      .filter(XmlFile.class)
      .map(DescriptorUtil::getIdeaPlugin)
      .filter(Conditions.notNull())
      .toList();
  }
}
