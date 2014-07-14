package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiElement;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 17.11.2004
 * Time: 19:24:40
 * To change this template use File | Settings | File Templates.
 */
class DeleteNodesAction implements Runnable {
  private final ArrayList elements;

  DeleteNodesAction(ArrayList _elements) {
    elements = _elements;
  }

  private void delete(PsiElement first, PsiElement last) throws Exception {
    if (last==first) {
      first.delete();
    } else {
      first.getParent().deleteChildRange(first,last);
    }
  }
  public void run() {
    try {
      PsiElement first= null;
      PsiElement last = null;

      for(int i = 0;i < elements.size(); ++i) {
        final PsiElement el = (PsiElement)elements.get(i);

        if (!el.isValid()) continue;
          
        if (first==null) {
          first = last = el;
        } else if (last.getNextSibling()==el) {
          last = el;
        } else {
          delete(first,last);
          first = last = null;
          --i;
          continue;
        }
      }

      if (first!=null) {
        delete(first,last);
      }
    } catch(Throwable ex) {
      ex.printStackTrace();
    } finally {
      elements.clear();
    }
  }
}
