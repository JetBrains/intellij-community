package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 17.11.2004
 * Time: 19:24:40
 * To change this template use File | Settings | File Templates.
 */
class DeleteNodesAction implements Runnable {

  private final List<PsiElement> elements;

  DeleteNodesAction(List<PsiElement> _elements) {
    elements = _elements;
  }

  private static void delete(PsiElement first, PsiElement last) {
    if (last == first) {
      first.delete();
    }
    else {
      first.getParent().deleteChildRange(first, last);
    }
  }

  @Override
  public void run() {
    try {
      PsiElement first = null;
      PsiElement last = null;

      for (PsiElement element : elements) {
        if (!element.isValid()) continue;

        if (first == null) {
          first = last = element;
        }
        else if (last.getNextSibling() == element) {
          last = element;
        }
        else {
          delete(first, last);
          first = last = element;
        }
      }

      if (first != null) {
        delete(first, last);
      }
    }
    finally {
      elements.clear();
    }
  }
}
