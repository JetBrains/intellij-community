package com.intellij.lang.ant.psi;

import com.intellij.extapi.psi.LightPsiFileBase;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.impl.AntProjectImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

public class AntFile extends LightPsiFileBase implements AntElement {

  private AntProject myProject;

  public AntFile(final FileViewProvider viewProvider) {
    super(viewProvider, AntSupport.getFileType().getLanguage() );
  }

  @NotNull
  public FileType getFileType() {
    return AntSupport.getFileType();
  }

  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[] {  };
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "AntFile:" + getName();
  }

  public void clearCaches() {
    myProject = null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public AntProject getAntProject() {
    if( myProject == null ) {
      final XmlFile xmlFile = (XmlFile)getManager().getElementFactory().createFileFromText("fake.xml", StdFileTypes.XML, getText());
      myProject = new AntProjectImpl(xmlFile);
    }
    return myProject;
  }
}
