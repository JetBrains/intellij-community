package org.jetbrains.android.dom.wrappers;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTarget;
import com.intellij.psi.impl.RenameableFakePsiElement;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.resourceManagers.ValueResourceInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
* @author Eugene.Kudelevsky
*/
public class LazyValueResourceElementWrapper extends RenameableFakePsiElement implements PsiTarget {
  private final ValueResourceInfo myResourceInfo;
  private final PsiElement myParent;

  public LazyValueResourceElementWrapper(@NotNull ValueResourceInfo resourceInfo, @NotNull PsiElement parent) {
    super(parent);
    myParent = parent;
    myResourceInfo = resourceInfo;
  }

  @Override
  public String getName() {
    return myResourceInfo.getName();
  }

  @Nullable
  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final XmlAttributeValue element = computeElement();
    if (element == null) {
      throw new IncorrectOperationException(
        "Cannot find resource '" + myResourceInfo.getName() + "' in file " + myResourceInfo.getContainingFile().getPath());
    }
    return new ValueResourceElementWrapper(element).setName(name);
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Nullable
      public String getPresentableText() {
        final String name = myResourceInfo.getName();
        final VirtualFile file = myResourceInfo.getContainingFile();
        final VirtualFile dir = file.getParent();
        if (dir == null) {
          return name;
        }
        return name + " (..." + File.separatorChar + dir.getName() +
               File.separatorChar + file.getName() + ')';
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return null;
      }
    };
  }

  @Nullable
  public XmlAttributeValue computeElement() {
    return myResourceInfo.computeXmlElement();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    final XmlAttributeValue element = myResourceInfo.computeXmlElement();
    return element != null ? element : myParent;
  }

  @NotNull
  public ValueResourceInfo getResourceInfo() {
    return myResourceInfo;
  }

  @Override
  public String getTypeName() {
    return "Android Value Resource";
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Nullable
  public static PsiElement computeLazyElement(PsiElement element) {
    if (element instanceof LazyValueResourceElementWrapper) {
      element = ((LazyValueResourceElementWrapper)element).computeElement();
    }
    return element;
  }
}
