package com.intellij.testFramework.utils.parameterInfo;

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
* Created by IntelliJ IDEA.
* User: Maxim.Mossienko
* Date: 01.10.2009
* Time: 21:44:31
* To change this template use File | Settings | File Templates.
*/
public class MockCreateParameterInfoContext implements CreateParameterInfoContext {
  private Object[] myItems;
  private PsiElement myHighlightedElement;
  private final Editor myEditor;
  private final PsiFile myFile;

  public MockCreateParameterInfoContext(@NotNull Editor editor, @NotNull PsiFile file) {
    myEditor = editor;
    myFile = file;
  }

  public Object[] getItemsToShow() {
    return myItems;
  }

  public void setItemsToShow(Object[] items) {
    myItems = items;
  }

  public void showHint(PsiElement element, int offset, ParameterInfoHandler handler) {}

  public int getParameterListStart() {
    return myEditor.getCaretModel().getOffset();
  }

  public PsiElement getHighlightedElement() {
    return myHighlightedElement;
  }

  public void setHighlightedElement(PsiElement elements) {
    myHighlightedElement = elements;
  }

  public Project getProject() {
    return myFile.getProject();
  }

  public PsiFile getFile() {
    return myFile;
  }

  public int getOffset() {
    return myEditor.getCaretModel().getOffset();
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }
}
