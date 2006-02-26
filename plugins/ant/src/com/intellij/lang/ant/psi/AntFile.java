package com.intellij.lang.ant.psi;

import com.intellij.extapi.psi.MetadataPsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.impl.AntASTNode;
import com.intellij.lang.ant.psi.impl.AntProjectImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class AntFile extends MetadataPsiFileBase implements AntElement {

  private final HashMap<AntASTNode, ASTNode> myAnt2SourceNodes;
  private final HashMap<ASTNode, AntASTNode> mySource2AntNodes;
  private AntProject myProject;
  private PsiElement[] myChildren = null;
  private AntASTNode myFileNode = null;

  public AntFile(final FileViewProvider viewProvider) {
    super(viewProvider, AntSupport.getLanguage());
    myAnt2SourceNodes = new HashMap<AntASTNode, ASTNode>();
    mySource2AntNodes = new HashMap<ASTNode, AntASTNode>();
  }

  @NotNull
  public FileType getFileType() {
    return AntSupport.getFileType();
  }

  @NotNull
  public PsiElement[] getChildren() {
    if (myChildren == null) {
      createFileStructure();
      myChildren = new PsiElement[]{myProject};
    }
    return myChildren;
  }

  public PsiElement getFirstChild() {
    final PsiElement[] psiElements = getChildren();
    return (psiElements.length > 0) ? psiElements[0] : null;
  }

  public PsiElement getLastChild() {
    final PsiElement[] psiElements = getChildren();
    return (psiElements.length > 0) ? psiElements[psiElements.length - 1] : null;
  }

  public ASTNode getNode() {
    createFileStructure();
    return myFileNode;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "AntFile:" + getName();
  }

  public void clearCaches() {
    myProject = null;
    myChildren = null;
    myFileNode = null;
    myAnt2SourceNodes.clear();
    mySource2AntNodes.clear();
  }

  public void registerAntNode(@NotNull AntASTNode node) {
    final ASTNode sourceNode = node.getSourceNode();
    myAnt2SourceNodes.put(node, sourceNode);
    mySource2AntNodes.put(sourceNode, node);
  }

  public void unregisterAntNode(@NotNull AntASTNode node) {
    myAnt2SourceNodes.remove(node);
    mySource2AntNodes.remove(node.getSourceNode());
  }

  public ASTNode getSourceNode(AntASTNode node) {
    return myAnt2SourceNodes.get(node);
  }

  public AntASTNode getAntNode(ASTNode sourceNode) {
    return mySource2AntNodes.get(sourceNode);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void createFileStructure() {
    if (myProject == null) {
      if (getSourceFile() == null) {
        setSourceFile(getManager().getElementFactory().createFileFromText("fake.xml", StdFileTypes.XML, getText()));
      }
      myProject = new AntProjectImpl((XmlFile)getSourceFile(), this);
    }
    if (myFileNode == null) {
      myFileNode = new AntASTNode(getSourceFile().getNode(), this, this);
      registerAntNode(myFileNode);
    }
  }
}
