package org.jetbrains.android.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiNamedElement;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * @author yole
 */
public class ResourceDirectoryNode extends ProjectViewNode<PsiDirectory> {
  private final Map<String, AbstractTreeNode> myChildMap = new HashMap<String, AbstractTreeNode>();
  private final ArrayList<AbstractTreeNode> myChildren = new ArrayList<AbstractTreeNode>();
  private final PsiDirectoryNode myBaseNode;

  protected ResourceDirectoryNode(Project project,
                                  PsiDirectoryNode directory,
                                  ViewSettings viewSettings) {
    super(project, directory.getValue(), viewSettings);
    myBaseNode = directory;
  }


  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return false;   // TODO: implement
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    return myChildren;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.clearText();
    presentation.addText(getValue().getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    presentation.setPresentableText(getValue().getName());
    presentation.setIcon(getValue().getIcon(0));
  }

  public void collectChildren() {
    for (AbstractTreeNode child: myBaseNode.getChildren()) {
      Object value = child.getValue();
      if (value instanceof PsiNamedElement) {
        String name = ((PsiNamedElement)value).getName();
        if (!myChildMap.containsKey(name)) {
          myChildMap.put(name, child);
          myChildren.add(child);
        }
      }
      else {
        myChildren.add(child);
      }
    }
  }

  @Nullable
  @Override
  public String getTestPresentation() {
    return "ResourceDirectory:" + getValue().getName();
  }

  @Override
  public String toString() {
    return "ResourceDirectory:" + getValue().getName();
  }
}
