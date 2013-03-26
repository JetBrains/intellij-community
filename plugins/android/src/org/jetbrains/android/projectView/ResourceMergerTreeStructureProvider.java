package org.jetbrains.android.projectView;

import com.android.resources.ResourceFolderType;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class ResourceMergerTreeStructureProvider implements TreeStructureProvider {
  @Override
  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    if (!ApplicationManager.getApplication().isInternal()) {
      return children;
    }

    if (parent instanceof PsiDirectoryNode) {
      PsiDirectory directory = ((PsiDirectoryNode)parent).getValue();
      if (!directory.getName().equals("res")) {
        return children;
      }
      Module module = ModuleUtil.findModuleForPsiElement(directory);
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        return children;
      }
      return mergeResourceDirectories(children, settings);
    }
    return children;
  }

  private static Collection<AbstractTreeNode> mergeResourceDirectories(Collection<AbstractTreeNode> children, ViewSettings settings) {
    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    Map<ResourceFolderType, ResourceDirectoryNode> resourceDirectories = new HashMap<ResourceFolderType, ResourceDirectoryNode>();
    for (AbstractTreeNode child : children) {
      if (!(child instanceof PsiDirectoryNode)) {
        result.add(child);
        continue;
      }
      PsiDirectoryNode directoryNode = (PsiDirectoryNode)child;
      PsiDirectory directory = directoryNode.getValue();
      ResourceFolderType type = ResourceFolderType.getFolderType(directory.getName());
      if (type == null) {
        result.add(child);
        continue;
      }
      ResourceDirectoryNode node = resourceDirectories.get(type);
      if (node == null || !directory.getName().contains("-")) {
        node = new ResourceDirectoryNode(directoryNode.getProject(), directoryNode, settings);
        resourceDirectories.put(type, node);
      }
    }
    for (ResourceDirectoryNode node : resourceDirectories.values()) {
      node.collectChildren();
      result.add(node);
    }
    return result;
  }

  @Nullable
  @Override
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
