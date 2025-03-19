// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ViewerTreeStructure extends AbstractTreeStructure {
  private boolean myShowWhiteSpaces = true;
  private boolean myShowTreeNodes = true;

  private final Project myProject;
  private PsiElement myRootPsiElement;
  private final Object myRootElement = ObjectUtils.sentinel("Psi Viewer Root");

  public ViewerTreeStructure(@NotNull Project project) {
    myProject = project;
  }

  public void setRootPsiElement(PsiElement rootPsiElement) {
    myRootPsiElement = rootPsiElement;
  }

  public PsiElement getRootPsiElement() {
    return myRootPsiElement;
  }

  @Override
  public @NotNull Object getRootElement() {
    return myRootElement;
  }

  @Override
  public Object @NotNull [] getChildElements(@NotNull Object element) {
    if (myRootElement == element) {
      if (myRootPsiElement == null) {
        return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
      }
      if (!(myRootPsiElement instanceof PsiFile)) {
        return new Object[]{myRootPsiElement};
      }
      List<PsiFile> files = ((PsiFile)myRootPsiElement).getViewProvider().getAllFiles();
      return PsiUtilCore.toPsiFileArray(files);
    }
    return ApplicationManager.getApplication().runReadAction((Computable<Object[]>)() -> {
      if (myShowTreeNodes) {
        ArrayList<Object> list = new ArrayList<>();
        ASTNode root = element instanceof PsiElement? SourceTreeToPsiMap.psiElementToTree((PsiElement)element) :
                             element instanceof ASTNode? (ASTNode)element : null;
        if (element instanceof Inject) {
          root = SourceTreeToPsiMap.psiElementToTree(((Inject)element).getPsi());
        }

        if (root != null) {
          ASTNode child = root.getFirstChildNode();
          while (child != null) {
            if (myShowWhiteSpaces || child.getElementType() != TokenType.WHITE_SPACE) {
              PsiElement childElement = child.getPsi();
              list.add(childElement == null ? child : childElement);
            }
            child = child.getTreeNext();
          }
          PsiElement psi = root.getPsi();
          if (psi instanceof PsiLanguageInjectionHost) {
            InjectedLanguageManager.getInstance(myProject).enumerate(psi, (injectedPsi, places) -> list.add(new Inject(psi, injectedPsi)));
          }
        }
        return ArrayUtil.toObjectArray(list);
      }
      PsiElement[] elementChildren = ((PsiElement)element).getChildren();
      if (!myShowWhiteSpaces) {
        List<PsiElement> childrenList = new ArrayList<>(elementChildren.length);
        for (PsiElement psiElement : elementChildren) {
          if (psiElement instanceof PsiWhiteSpace) {
            continue;
          }
          childrenList.add(psiElement);
        }
        return PsiUtilCore.toPsiElementArray(childrenList);
      }
      return elementChildren;
    });
  }

  @Override
  public Object getParentElement(@NotNull Object element) {
    if (element == myRootElement) {
      return null;
    }
    if (element == myRootPsiElement) {
      return myRootElement;
    }
    if (element instanceof PsiFile) {
      PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(((PsiFile)element).getProject()).getInjectionHost((PsiFile)element);
      if (host != null) {
        return new Inject(host, (PsiElement)element);
      }
    }
    if (element instanceof Inject) {
      return ((Inject)element).getParent();
    }
    if (element instanceof PsiElement) {
      return ((PsiElement)element).getContext();
    }
    return null;
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public @NotNull NodeDescriptor<?> createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
    if (element == myRootElement) {
      return new NodeDescriptor<>(myProject, null) {
        @Override
        public boolean update() {
          return false;
        }
        @Override
        public Object getElement() {
          return myRootElement;
        }
      };
    }
    return new ViewerNodeDescriptor(myProject, element, parentDescriptor);
  }

  public void setShowWhiteSpaces(boolean showWhiteSpaces) {
    myShowWhiteSpaces = showWhiteSpaces;
  }

  public void setShowTreeNodes(boolean showTreeNodes) {
    myShowTreeNodes = showTreeNodes;
  }

  static class Inject {
    private final @NotNull PsiElement myParent;
    private final @NotNull PsiElement myPsi;

    Inject(@NotNull PsiElement parent, @NotNull PsiElement psi) {
      myParent = parent;
      myPsi = psi;
    }

    public @NotNull PsiElement getParent() {
      return myParent;
    }

    public @NotNull PsiElement getPsi() {
      return myPsi;
    }

    @Override
    public String toString() {
      return "INJECTION " + myPsi.getLanguage();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Inject inject = (Inject)o;

      if (!myParent.equals(inject.myParent)) return false;
      if (!myPsi.equals(inject.myPsi)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myParent.hashCode();
      result = 31 * result + myPsi.hashCode();
      return result;
    }
  }
}
